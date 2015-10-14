/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.template.formatters;

import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatter;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatterFactory;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.util.template.translation.TranslatorConfig;

import com.google.common.annotations.VisibleForTesting;

public class DefaultInvoiceFormatterFactory implements InvoiceFormatterFactory {

    private final Map<Currency, Locale> currencyLocaleMap = new HashMap<Currency, Locale>();

    public DefaultInvoiceFormatterFactory() {
        // This initialization relies on System.currentTimeMillis() instead of the Kill Bill clock (it won't be accurate when moving the clock)
        for (final Locale localeItem : Locale.getAvailableLocales()) {
            try {
                final java.util.Currency currency = java.util.Currency.getInstance(localeItem);
                currencyLocaleMap.put(currency, localeItem);
            } catch (final Exception ignored) {
            }
        }
    }

    @Override
    public InvoiceFormatter createInvoiceFormatter(final TranslatorConfig config, final Invoice invoice, final Locale locale, final CurrencyConversionApi currencyConversionApi,
                                                   final ResourceBundleFactory bundleFactory, final InternalTenantContext context) {
        return new DefaultInvoiceFormatter(config, invoice, locale, currencyConversionApi, bundleFactory, context, currencyLocaleMap);
    }

    @VisibleForTesting
    Map<Currency, Locale> getCurrencyLocaleMap() {
        return currencyLocaleMap;
    }
}
