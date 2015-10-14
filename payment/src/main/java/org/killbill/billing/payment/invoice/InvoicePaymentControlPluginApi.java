/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.payment.invoice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao;
import org.killbill.billing.payment.invoice.dao.PluginAutoPayOffModelDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public final class InvoicePaymentControlPluginApi implements PaymentControlPluginApi {

    public static final String CREATED_BY = "InvoicePaymentControlPluginApi";

    /* Don't change value String for properties as they are referenced from jaxrs without the constants which are not accessible */
    public static final String PLUGIN_NAME = "__INVOICE_PAYMENT_CONTROL_PLUGIN__";
    public static final String PROP_IPCD_INVOICE_ID = "IPCD_INVOICE_ID";
    public static final String PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY = "IPCD_REFUND_IDS_AMOUNTS";
    public static final String PROP_IPCD_REFUND_WITH_ADJUSTMENTS = "IPCD_REFUND_WITH_ADJUSTMENTS";

    private final PaymentConfig paymentConfig;
    private final InvoiceInternalApi invoiceApi;
    private final TagUserApi tagApi;
    private final PaymentDao paymentDao;
    private final InvoicePaymentControlDao controlDao;
    private final RetryServiceScheduler retryServiceScheduler;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;

    private final Logger log = LoggerFactory.getLogger(InvoicePaymentControlPluginApi.class);

    @Inject
    public InvoicePaymentControlPluginApi(final PaymentConfig paymentConfig, final InvoiceInternalApi invoiceApi, final TagUserApi tagApi, final PaymentDao paymentDao,
                                          final InvoicePaymentControlDao invoicePaymentControlDao,
                                          @Named(PaymentModule.RETRYABLE_NAMED) final RetryServiceScheduler retryServiceScheduler,
                                          final InternalCallContextFactory internalCallContextFactory, final Clock clock) {
        this.paymentConfig = paymentConfig;
        this.invoiceApi = invoiceApi;
        this.tagApi = tagApi;
        this.paymentDao = paymentDao;
        this.controlDao = invoicePaymentControlDao;
        this.retryServiceScheduler = retryServiceScheduler;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
    }

    @Override
    public PriorPaymentControlResult priorCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {

        final TransactionType transactionType = paymentControlContext.getTransactionType();
        Preconditions.checkArgument(paymentControlContext.getPaymentApiType() == PaymentApiType.PAYMENT_TRANSACTION);
        Preconditions.checkArgument(transactionType == TransactionType.PURCHASE ||
                                    transactionType == TransactionType.REFUND ||
                                    transactionType == TransactionType.CHARGEBACK);

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(paymentControlContext.getAccountId(), paymentControlContext);
        switch (transactionType) {
            case PURCHASE:
                return getPluginPurchaseResult(paymentControlContext, pluginProperties, internalContext);
            case REFUND:
                return getPluginRefundResult(paymentControlContext, pluginProperties, internalContext);
            case CHARGEBACK:
                return new DefaultPriorPaymentControlResult(false, paymentControlContext.getAmount());
            default:
                throw new IllegalStateException("Unexpected transactionType " + transactionType);
        }
    }

    @Override
    public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {
        final TransactionType transactionType = paymentControlContext.getTransactionType();
        Preconditions.checkArgument(transactionType == TransactionType.PURCHASE ||
                                    transactionType == TransactionType.REFUND ||
                                    transactionType == TransactionType.CHARGEBACK);

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(paymentControlContext.getAccountId(), paymentControlContext);
        try {
            final InvoicePayment existingInvoicePayment;
            switch (transactionType) {
                case PURCHASE:
                    final UUID invoiceId = getInvoiceId(pluginProperties);
                    existingInvoicePayment = invoiceApi.getInvoicePaymentForAttempt(paymentControlContext.getPaymentId(), internalContext);
                    if (existingInvoicePayment != null && existingInvoicePayment.isSuccess()) {
                        log.info("onSuccessCall was already completed for payment purchase :" + paymentControlContext.getPaymentId());
                    } else {
                        invoiceApi.notifyOfPayment(invoiceId,
                                                   paymentControlContext.getAmount(),
                                                   paymentControlContext.getCurrency(),
                                                   paymentControlContext.getProcessedCurrency(),
                                                   paymentControlContext.getPaymentId(),
                                                   paymentControlContext.getCreatedDate(),
                                                   true,
                                                   internalContext);
                    }
                    break;

                case REFUND:
                    existingInvoicePayment = invoiceApi.getInvoicePaymentForRefund(paymentControlContext.getPaymentId(), internalContext);
                    if (existingInvoicePayment != null) {
                        log.info("onSuccessCall was already completed for payment refund :" + paymentControlContext.getPaymentId());
                    } else {
                        final Map<UUID, BigDecimal> idWithAmount = extractIdsWithAmountFromProperties(pluginProperties);
                        final PluginProperty prop = getPluginProperty(pluginProperties, PROP_IPCD_REFUND_WITH_ADJUSTMENTS);
                        final boolean isAdjusted = prop != null ? Boolean.valueOf((String) prop.getValue()) : false;
                        invoiceApi.createRefund(paymentControlContext.getPaymentId(), paymentControlContext.getAmount(), isAdjusted, idWithAmount, paymentControlContext.getTransactionExternalKey(), internalContext);
                    }
                    break;

                case CHARGEBACK:
                    existingInvoicePayment = invoiceApi.getInvoicePaymentForChargeback(paymentControlContext.getPaymentId(), internalContext);
                    if (existingInvoicePayment != null) {
                        log.info("onSuccessCall was already completed for payment chargeback :" + paymentControlContext.getPaymentId());
                    } else {
                        invoiceApi.createChargeback(paymentControlContext.getPaymentId(), paymentControlContext.getProcessedAmount(), paymentControlContext.getProcessedCurrency(), internalContext);
                    }
                    break;

                default:
                    throw new IllegalStateException("Unexpected transactionType " + transactionType);
            }
        } catch (final InvoiceApiException e) {
            log.error("InvoicePaymentControlPluginApi onSuccessCall failed for attemptId = " + paymentControlContext.getAttemptPaymentId() + ", transactionType  = " + transactionType, e);
        }
        return new DefaultOnSuccessPaymentControlResult();
    }

    @Override
    public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(paymentControlContext.getAccountId(), paymentControlContext);
        final TransactionType transactionType = paymentControlContext.getTransactionType();
        switch (transactionType) {
            case PURCHASE:
                final UUID invoiceId = getInvoiceId(pluginProperties);
                if (paymentControlContext.getPaymentId() != null) {
                    try {
                        invoiceApi.notifyOfPayment(invoiceId,
                                                   paymentControlContext.getAmount(),
                                                   paymentControlContext.getCurrency(),
                                                   // processed currency may be null so we use currency; processed currency will be updated if/when payment succeeds
                                                   paymentControlContext.getCurrency(),
                                                   paymentControlContext.getPaymentId(),
                                                   paymentControlContext.getCreatedDate(),
                                                   false,
                                                   internalContext);
                    } catch (InvoiceApiException e) {
                        log.error("InvoicePaymentControlPluginApi onFailureCall failed ton update invoice for attemptId = " + paymentControlContext.getAttemptPaymentId() + ", transactionType  = " + transactionType, e);
                    }
                }

                final DateTime nextRetryDate = computeNextRetryDate(paymentControlContext.getPaymentExternalKey(), paymentControlContext.isApiPayment(), internalContext);
                return new DefaultFailureCallResult(nextRetryDate);
            case REFUND:
            case CHARGEBACK:
                // We don't retry  REFUND, CHARGEBACK
                return new DefaultFailureCallResult(null);
            default:
                throw new IllegalStateException("Unexpected transactionType " + transactionType);
        }
    }

    public void process_AUTO_PAY_OFF_removal(final UUID accountId, final InternalCallContext internalCallContext) {
        final List<PluginAutoPayOffModelDao> entries = controlDao.getAutoPayOffEntry(accountId);
        for (final PluginAutoPayOffModelDao cur : entries) {
            // TODO In theory we should pass not only PLUGIN_NAME, but also all the plugin list associated which the original call
            retryServiceScheduler.scheduleRetry(ObjectType.ACCOUNT, accountId, cur.getAttemptId(), internalCallContext.getTenantRecordId(), ImmutableList.<String>of(PLUGIN_NAME), clock.getUTCNow());
        }
        controlDao.removeAutoPayOffEntry(accountId);
    }

    private UUID getInvoiceId(final Iterable<PluginProperty> pluginProperties) throws PaymentControlApiException {
        final PluginProperty invoiceProp = getPluginProperty(pluginProperties, PROP_IPCD_INVOICE_ID);
        if (invoiceProp == null ||
            !(invoiceProp.getValue() instanceof String)) {
            throw new PaymentControlApiException("Need to specify a valid invoiceId in property " + PROP_IPCD_INVOICE_ID);
        }
        return UUID.fromString((String) invoiceProp.getValue());
    }

    private PriorPaymentControlResult getPluginPurchaseResult(final PaymentControlContext paymentControlPluginContext, final Iterable<PluginProperty> pluginProperties, final InternalCallContext internalContext) throws PaymentControlApiException {
        try {
            final UUID invoiceId = getInvoiceId(pluginProperties);
            final Invoice invoice = rebalanceAndGetInvoice(invoiceId, internalContext);
            final BigDecimal requestedAmount = validateAndComputePaymentAmount(invoice, paymentControlPluginContext.getAmount(), paymentControlPluginContext.isApiPayment());

            final boolean isAborted = requestedAmount.compareTo(BigDecimal.ZERO) == 0;
            if (!isAborted && insert_AUTO_PAY_OFF_ifRequired(paymentControlPluginContext, requestedAmount)) {
                return new DefaultPriorPaymentControlResult(true);
            }

            if (paymentControlPluginContext.isApiPayment() && isAborted) {
                throw new PaymentControlApiException("Payment for invoice " + invoice.getId() +
                                                     " aborted : invoice balance is = " + invoice.getBalance() +
                                                     ", requested payment amount is = " + paymentControlPluginContext.getAmount());
            } else {
                return new DefaultPriorPaymentControlResult(isAborted, requestedAmount);
            }
        } catch (final InvoiceApiException e) {
            throw new PaymentControlApiException(e);
        } catch (final IllegalArgumentException e) {
            throw new PaymentControlApiException(e);
        }
    }

    private PriorPaymentControlResult getPluginRefundResult(final PaymentControlContext paymentControlPluginContext, final Iterable<PluginProperty> pluginProperties, final InternalCallContext internalContext) throws PaymentControlApiException {
        final Map<UUID, BigDecimal> idWithAmount = extractIdsWithAmountFromProperties(pluginProperties);
        if ((paymentControlPluginContext.getAmount() == null || paymentControlPluginContext.getAmount().compareTo(BigDecimal.ZERO) == 0) &&
            idWithAmount.size() == 0) {
            throw new PaymentControlApiException("Refund for payment, key = " + paymentControlPluginContext.getPaymentExternalKey() +
                                                 " aborted: requested refund amount is = " + paymentControlPluginContext.getAmount());
        }

        final PaymentModelDao payment = paymentDao.getPayment(paymentControlPluginContext.getPaymentId(), internalContext);
        if (payment == null) {
            throw new PaymentControlApiException();
        }
        // This will calculate the upper bound on the refund amount based on the invoice items associated with that payment.
        // Note that we are not checking that other (partial) refund occurred, but if the refund ends up being greater than waht is allowed
        // the call to the gateway would fail; it would need noce to validate on our side though...
        final BigDecimal amountToBeRefunded = computeRefundAmount(payment.getId(), paymentControlPluginContext.getAmount(), idWithAmount, internalContext);
        final boolean isAborted = amountToBeRefunded.compareTo(BigDecimal.ZERO) == 0;

        if (paymentControlPluginContext.isApiPayment() && isAborted) {
            throw new PaymentControlApiException("Refund for payment " + payment.getId() +
                                                 " aborted : invoice item sum amount is " + amountToBeRefunded +
                                                 ", requested refund amount is = " + paymentControlPluginContext.getAmount());
        } else {
            return new DefaultPriorPaymentControlResult(isAborted, amountToBeRefunded);
        }
    }

    private Map<UUID, BigDecimal> extractIdsWithAmountFromProperties(final Iterable<PluginProperty> properties) {
        final PluginProperty prop = getPluginProperty(properties, PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY);
        if (prop == null) {
            return ImmutableMap.<UUID, BigDecimal>of();
        }
        return (Map<UUID, BigDecimal>) prop.getValue();
    }

    private PluginProperty getPluginProperty(final Iterable<PluginProperty> properties, final String propertyName) {
        return Iterables.tryFind(properties, new Predicate<PluginProperty>() {
            @Override
            public boolean apply(final PluginProperty input) {
                return input.getKey().equals(propertyName);
            }
        }).orNull();
    }

    private BigDecimal computeRefundAmount(final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                                           final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalTenantContext context)
            throws PaymentControlApiException {

        if (invoiceItemIdsWithAmounts.size() == 0) {
            if (specifiedRefundAmount == null || specifiedRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentControlApiException("You need to specify positive a refund amount");
            }
            return specifiedRefundAmount;
        }

        final List<InvoiceItem> items;
        try {
            items = invoiceApi.getInvoiceForPaymentId(paymentId, context).getInvoiceItems();

            BigDecimal amountFromItems = BigDecimal.ZERO;
            for (final UUID itemId : invoiceItemIdsWithAmounts.keySet()) {
                final BigDecimal specifiedItemAmount = invoiceItemIdsWithAmounts.get(itemId);
                final BigDecimal itemAmount = getAmountFromItem(items, itemId);
                if (specifiedItemAmount != null &&
                    (specifiedItemAmount.compareTo(BigDecimal.ZERO) <= 0 || specifiedItemAmount.compareTo(itemAmount) > 0)) {
                    throw new PaymentControlApiException("You need to specify valid invoice item amount ");
                }
                amountFromItems = amountFromItems.add(Objects.firstNonNull(specifiedItemAmount, itemAmount));
            }
            return amountFromItems;
        } catch (final InvoiceApiException e) {
            throw new PaymentControlApiException(e);
        }
    }

    private BigDecimal getAmountFromItem(final List<InvoiceItem> items, final UUID itemId) throws PaymentControlApiException {
        for (final InvoiceItem item : items) {
            if (item.getId().equals(itemId)) {
                return item.getAmount();
            }
        }
        throw new PaymentControlApiException("Unable to find invoice item for id " + itemId);
    }

    private DateTime computeNextRetryDate(final String paymentExternalKey, final boolean isApiAPayment, final InternalCallContext internalContext) {

        // Don't retry call that come from API.
        if (isApiAPayment) {
            return null;
        }

        final List<PaymentTransactionModelDao> purchasedTransactions = getPurchasedTransactions(paymentExternalKey, internalContext);
        if (purchasedTransactions.size() == 0) {
            return null;
        }
        final PaymentTransactionModelDao lastTransaction = purchasedTransactions.get(purchasedTransactions.size() - 1);
        switch (lastTransaction.getTransactionStatus()) {
            case PAYMENT_FAILURE:
                return getNextRetryDateForPaymentFailure(purchasedTransactions);

            case PLUGIN_FAILURE:
                return getNextRetryDateForPluginFailure(purchasedTransactions);

            case UNKNOWN:
            default:
                return null;
        }
    }

    private DateTime getNextRetryDateForPaymentFailure(final List<PaymentTransactionModelDao> purchasedTransactions) {

        DateTime result = null;
        final List<Integer> retryDays = paymentConfig.getPaymentFailureRetryDays();
        final int attemptsInState = getNumberAttemptsInState(purchasedTransactions, TransactionStatus.PAYMENT_FAILURE);
        final int retryCount = (attemptsInState - 1) >= 0 ? (attemptsInState - 1) : 0;
        if (retryCount < retryDays.size()) {
            final int retryInDays;
            final DateTime nextRetryDate = clock.getUTCNow();
            try {
                retryInDays = retryDays.get(retryCount);
                result = nextRetryDate.plusDays(retryInDays);
            } catch (final NumberFormatException ex) {
                log.error("Could not get retry day for retry count {}", retryCount);
            }
        }
        return result;
    }

    private DateTime getNextRetryDateForPluginFailure(final List<PaymentTransactionModelDao> purchasedTransactions) {

        DateTime result = null;
        final int attemptsInState = getNumberAttemptsInState(purchasedTransactions, TransactionStatus.PLUGIN_FAILURE);
        final int retryAttempt = (attemptsInState - 1) >= 0 ? (attemptsInState - 1) : 0;

        if (retryAttempt < paymentConfig.getPluginFailureRetryMaxAttempts()) {
            int nbSec = paymentConfig.getPluginFailureInitialRetryInSec();
            int remainingAttempts = retryAttempt;
            while (--remainingAttempts > 0) {
                nbSec = nbSec * paymentConfig.getPluginFailureRetryMultiplier();
            }
            result = clock.getUTCNow().plusSeconds(nbSec);
        }
        return result;
    }

    private int getNumberAttemptsInState(final Collection<PaymentTransactionModelDao> allTransactions, final TransactionStatus... statuses) {
        if (allTransactions == null || allTransactions.size() == 0) {
            return 0;
        }
        return Collections2.filter(allTransactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                for (final TransactionStatus cur : statuses) {
                    if (input.getTransactionStatus() == cur) {
                        return true;
                    }
                }
                return false;
            }
        }).size();
    }

    private List<PaymentTransactionModelDao> getPurchasedTransactions(final String paymentExternalKey, final InternalCallContext internalContext) {
        final PaymentModelDao payment = paymentDao.getPaymentByExternalKey(paymentExternalKey, internalContext);
        if (payment == null) {
            return Collections.emptyList();
        }
        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(payment.getId(), internalContext);
        if (transactions == null || transactions.size() == 0) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(Iterables.filter(transactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getTransactionType() == TransactionType.PURCHASE;
            }
        }));
    }

    private Invoice rebalanceAndGetInvoice(final UUID invoiceId, final InternalCallContext context) throws InvoiceApiException {
        final Invoice invoicePriorRebalancing = invoiceApi.getInvoiceById(invoiceId, context);
        invoiceApi.consumeExistingCBAOnAccountWithUnpaidInvoices(invoicePriorRebalancing.getAccountId(), context);
        final Invoice invoice = invoiceApi.getInvoiceById(invoiceId, context);
        return invoice;
    }

    private BigDecimal validateAndComputePaymentAmount(final Invoice invoice, @Nullable final BigDecimal inputAmount, final boolean isApiPayment) {

        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Invoice " + invoice.getId() + " has already been paid");
            return BigDecimal.ZERO;
        }
        if (isApiPayment &&
            inputAmount != null &&
            invoice.getBalance().compareTo(inputAmount) < 0) {
            log.info("Invoice " + invoice.getId() +
                     " has a balance of " + invoice.getBalance().floatValue() +
                     " less than retry payment amount of " + inputAmount.floatValue());
            return BigDecimal.ZERO;
        }
        if (inputAmount == null) {
            return invoice.getBalance();
        } else {
            return invoice.getBalance().compareTo(inputAmount) < 0 ? invoice.getBalance() : inputAmount;
        }
    }

    private boolean insert_AUTO_PAY_OFF_ifRequired(final PaymentControlContext paymentControlContext, final BigDecimal computedAmount) {
        if (paymentControlContext.isApiPayment() || !isAccountAutoPayOff(paymentControlContext.getAccountId(), paymentControlContext)) {
            return false;
        }
        final PluginAutoPayOffModelDao data = new PluginAutoPayOffModelDao(paymentControlContext.getAttemptPaymentId(), paymentControlContext.getPaymentExternalKey(), paymentControlContext.getTransactionExternalKey(),
                                                                           paymentControlContext.getAccountId(), PLUGIN_NAME,
                                                                           paymentControlContext.getPaymentId(), paymentControlContext.getPaymentMethodId(),
                                                                           computedAmount, paymentControlContext.getCurrency(), CREATED_BY, clock.getUTCNow());
        controlDao.insertAutoPayOff(data);
        return true;
    }

    private boolean isAccountAutoPayOff(final UUID accountId, final CallContext callContext) {
        final List<Tag> accountTags = tagApi.getTagsForAccount(accountId, false, callContext);
        return ControlTagType.isAutoPayOff(Collections2.transform(accountTags, new Function<Tag, UUID>() {
            @Override
            public UUID apply(final Tag tag) {
                return tag.getTagDefinitionId();
            }
        }));
    }
}
