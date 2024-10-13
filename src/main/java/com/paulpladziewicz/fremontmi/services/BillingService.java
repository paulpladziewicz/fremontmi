package com.paulpladziewicz.fremontmi.services;

import com.paulpladziewicz.fremontmi.exceptions.StripeServiceException;
import com.paulpladziewicz.fremontmi.models.*;
import com.paulpladziewicz.fremontmi.repositories.ContentRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillingService {

    private static final Logger logger = LoggerFactory.getLogger(BillingService.class);

    private final UserService userService;

    private final EmailService emailService;

    private final ContentRepository contentRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    public BillingService(UserService userService, EmailService emailService, ContentRepository contentRepository) {
        this.userService = userService;
        this.emailService = emailService;
        this.contentRepository = contentRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public String getCustomerId() {
        UserProfile userProfile = userService.getUserProfile();

        if (userProfile.getStripeCustomerId() != null) {
            return userProfile.getStripeCustomerId();
        }

        return createCustomer(userProfile);
    }

    public String createCustomer(UserProfile userProfile) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(userProfile.getFirstName() + ' ' + userProfile.getLastName())
                    .setEmail(userProfile.getEmail())
                    .build();

            Customer customer = Customer.create(params);

            if (customer == null) {
                throw new StripeServiceException("Failed to create a Stripe customer. Stripe returned null.");
            }

            userProfile.setStripeCustomerId(customer.getId());
            userService.saveUserProfile(userProfile);

            return customer.getId();

        } catch (StripeException e) {
            logger.error("Error creating Stripe customer: ", e);
            throw new StripeServiceException("Error occurred while creating a Stripe customer.", e);
        }
    }

    public Map<String, Object> createSubscription(String priceId) {
        String customerId = getCustomerId(); // This method should throw an exception if the customer ID cannot be found

        SubscriptionCreateParams subCreateParams = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .setPaymentSettings(
                        SubscriptionCreateParams.PaymentSettings.builder()
                                .setPaymentMethodTypes(
                                        java.util.List.of(SubscriptionCreateParams.PaymentSettings.PaymentMethodType.CARD)
                                )
                                .build()
                )
                .addExpand("latest_invoice.payment_intent") // TODO do I need this?
                .build();

        try {
            Subscription subscription = Subscription.create(subCreateParams);

            String clientSecret = subscription.getLatestInvoiceObject()
                    .getPaymentIntentObject()
                    .getClientSecret();

            if (clientSecret == null) {
                logger.error("ClientSecret is null for subscription: {}", subscription.getId());
                throw new StripeServiceException("Failed to retrieve client secret for payment intent.");
            }

            Map<String, Object> subscriptionData = new HashMap<>();
            subscriptionData.put("subscriptionId", subscription.getId());
            subscriptionData.put("clientSecret", clientSecret);

            if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
                SubscriptionItem subscriptionItem = subscription.getItems().getData().getFirst();

                Price price = subscriptionItem.getPrice();

                String displayName = price.getMetadata().get("displayName");
                String displayPrice = price.getMetadata().get("displayPrice");

                if (displayName != null) {
                    subscriptionData.put("displayName", displayName);
                }
                if (displayPrice != null) {
                    subscriptionData.put("displayPrice", displayPrice);
                }

                subscriptionData.put("priceId", price.getId());
            }

            Invoice latestInvoice = subscription.getLatestInvoiceObject();
            if (latestInvoice != null) {
                subscriptionData.put("invoiceId", latestInvoice.getId());
            } else {
                logger.warn("Latest invoice is null for subscription: {}", subscription.getId());
            }

            // TODO store when a new subscription should be requested

            return subscriptionData;
        } catch (StripeException e) {
            logger.error("Failed to create subscription due to a Stripe exception", e);
            throw new StripeServiceException("Failed to create subscription with Stripe.", e);
        }
    }

    public List<Subscription> getSubscriptions() {
        UserProfile userProfile = userService.getUserProfile();
        String stripeCustomerId = userProfile.getStripeCustomerId();

        if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            SubscriptionListParams params = SubscriptionListParams.builder()
                    .setStatus(SubscriptionListParams.Status.ACTIVE) // Only fetch active subscriptions
                    .setCustomer(userProfile.getStripeCustomerId())
                    .addExpand("data.default_payment_method")
                    .addExpand("data.latest_invoice") // Ensure latest invoice is fetched
                    .build();

            SubscriptionCollection subscriptions = Subscription.list(params);

            return subscriptions.getData().stream()
                    .filter(subscription -> "active".equals(subscription.getStatus())) // Check active status correctly
                    .filter(subscription -> {
                        Invoice latestInvoice = subscription.getLatestInvoiceObject(); // Use getLatestInvoiceObject()
                        return latestInvoice != null && "paid".equals(latestInvoice.getStatus());
                    })
                    .collect(Collectors.toList());
        } catch (StripeException e) {
            logger.error("Failed to retrieve subscriptions due to a Stripe exception", e);
            throw new StripeServiceException("Failed to retrieve subscriptions with Stripe.", e);
        }
    }

    public List<Invoice> getInvoices() {
        UserProfile userProfile = userService.getUserProfile();
        String stripeCustomerId = userProfile.getStripeCustomerId();

        if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            InvoiceListParams params = InvoiceListParams.builder()
                    .setCustomer(userProfile.getStripeCustomerId())
                    .build();

            InvoiceCollection invoiceCollection = Invoice.list(params);

            return invoiceCollection.getData().stream()
                    .filter(invoice -> "paid".equals(invoice.getStatus())) // Check if the invoice status is 'paid'
                    .collect(Collectors.toList());
        } catch (StripeException e) {
            logger.error("Failed to retrieve invoices due to a Stripe exception", e);
            throw new StripeServiceException("Failed to retrieve invoices with Stripe.", e);
        }
    }

    public void cancelSubscriptionAtPeriodEnd(String subscriptionId) {
        UserProfile userProfile = userService.getUserProfile();

        if (userProfile.getStripeCustomerId() == null) {
            throw new StripeServiceException("No Stripe customer ID associated with this user.");
        }

        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);

            SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();

            subscription.update(updateParams);

        } catch (StripeException e) {
            logger.error("Error setting cancel_at_period_end flag for subscription: ", e);
            throw new StripeServiceException("Failed to cancel subscription at period end.", e);
        }
    }

    public void resumeSubscription(String subscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);

            if (subscription.getCancelAtPeriodEnd()) {
                // Unset the cancel_at_period_end flag
                SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(false)
                        .build();

                subscription.update(updateParams);

            } else {
                throw new StripeServiceException("Subscription is not set to cancel at period end.");
            }

        } catch (StripeException e) {
            logger.error("Error resuming subscription: ", e);
            throw new StripeServiceException("Failed to resume subscription with Stripe.", e);
        }
    }


    public ResponseEntity<String> handleStripeWebhook(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

            switch (event.getType()) {
                case "invoice.payment_failed":
                    Invoice failedInvoice = (Invoice) dataObjectDeserializer.getObject().orElse(null);
                    if (failedInvoice != null) {
                        handlePaymentFailed(failedInvoice);
                        // there is subscription data within; may not need invoice is
                        System.out.println("Payment failed");
                    }
                    break;

                case "customer.subscription.deleted":
                    Subscription subscription = (Subscription) dataObjectDeserializer.getObject().orElse(null);
                    if (subscription != null) {
                        handleSubscriptionCancellation(subscription);
                        System.out.println("Subscription canceled");
                    }
                    break;

                case "charge.dispute.created":
                    Dispute dispute = (Dispute) dataObjectDeserializer.getObject().orElse(null);
                    if (dispute != null) {
                        emailService.sendDisputeCreatedEmailAsync("ppladziewicz@gmail.com", dispute);
                        logger.info("Dispute created and email sent for dispute ID {}", dispute.getId());
                    }
                    break;
            }

            return ResponseEntity.ok("");
        } catch (SignatureVerificationException e) {
            // Invalid signature error, reject the request
            logger.error("Invalid Stripe webhook signature: ", e);
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            // General error while processing the event
            logger.error("Error handling Stripe webhook: ", e);
            return ResponseEntity.status(400).body("Webhook handling error");
        }
    }

    private void handleSubscriptionCancellation(Subscription subscription) {
        String subscriptionId = subscription.getId();

        // Retrieve the content by subscriptionId from the repository
        Optional<Content> optionalContent = contentRepository.findByStripeDetails_SubscriptionId(subscriptionId);

        if (optionalContent.isPresent()) {
            Content content = optionalContent.get();

            // Mark the content or subscription as canceled, you can set a status field or similar
            content.setStatus(ContentStatus.REQUIRES_ACTIVE_SUBSCRIPTION.getStatus());
            content.setVisibility(ContentVisibility.RESTRICTED.getVisibility());
            contentRepository.save(content);

            logger.info("Subscription {} canceled and content updated.", subscriptionId);
        } else {
            logger.warn("Content not found for subscription ID {}", subscriptionId);
        }
    }

    private void handlePaymentFailed(Invoice failedInvoice) {
        String invoiceId = failedInvoice.getId();

        Optional<Content> optionalContent = contentRepository.findByStripeDetails_InvoiceId(invoiceId);

        if (optionalContent.isPresent()) {
            Content content = optionalContent.get();

            content.setStatus(ContentStatus.PAYMENT_FAILED.getStatus());
            contentRepository.save(content);

            logger.info("Payment failed for invoice {} and content updated.", invoiceId);
        } else {
            logger.warn("Content not found for invoice ID {}", invoiceId);
        }
    }

    public ServiceResponse<Content> handleSubscriptionSuccess(PaymentRequest paymentRequest) {
        String contentId = paymentRequest.getEntityId();
        String paymentStatus = paymentRequest.getPaymentStatus();

        if (!paymentStatus.equals("succeeded")) {
            logger.error("Payment not successful but it should have been when handling successful payment. Payment status: {} neighborServiceProfileId: {}", paymentStatus, paymentRequest.getEntityId());
            return ServiceResponse.error("payment_not_successful");
        }

        Optional<Content> optionalContent = contentRepository.findById(contentId);

        if (optionalContent.isEmpty()) {
            return ServiceResponse.error("content_not_found");
        }

        Content content = optionalContent.get();
        content.setStatus(ContentStatus.ACTIVE.getStatus());
        content.setVisibility(ContentVisibility.PUBLIC.getVisibility());

        try {
            Content savedContent = contentRepository.save(content);

            return ServiceResponse.value(savedContent);
        } catch (Exception e) {
            logger.error("Error saving content after handling successful payment: ", e);
            return ServiceResponse.error("content_save_error");
        }
    }
}
