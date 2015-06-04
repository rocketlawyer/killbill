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

package org.killbill.billing.catalog.caching;

import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Unit;

public class PluginStaticCatalogAdapter implements StaticCatalog {

    private final PluginCatalogAdapter adapter;
    private final DateTime effectiveDate;

    public PluginStaticCatalogAdapter(final PluginCatalogAdapter adapter, final DateTime effectiveDate) {
        this.adapter = adapter;
        this.effectiveDate = effectiveDate;
    }

    @Override
    public String getCatalogName() {
        return adapter.getCatalogName();
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return adapter.vpc.getRecurringBillingMode();
    }

    @Override
    public Date getEffectiveDate() throws CatalogApiException {
        return effectiveDate.toDate();
    }

    @Override
    public Currency[] getCurrentSupportedCurrencies() throws CatalogApiException {
        return adapter.getSupportedCurrencies(effectiveDate);
    }

    @Override
    public Product[] getCurrentProducts() throws CatalogApiException {
        return adapter.getProducts(effectiveDate);
    }

    @Override
    public Unit[] getCurrentUnits() throws CatalogApiException {
        return adapter.getUnits(effectiveDate);
    }

    @Override
    public Plan[] getCurrentPlans() throws CatalogApiException {
        return adapter.getPlans(effectiveDate);
    }

    @Override
    public Plan createOrFindCurrentPlan(final String productName, final BillingPeriod billingPeriod, final String priceList, final PlanPhasePriceOverridesWithCallContext overrides) throws CatalogApiException {
        return adapter.createOrFindPlan(productName, billingPeriod, priceList, overrides, effectiveDate);
    }

    @Override
    public Plan findCurrentPlan(final String name) throws CatalogApiException {
        return adapter.findPlan(name, effectiveDate);
    }

    @Override
    public Product findCurrentProduct(final String name) throws CatalogApiException {
        return adapter.findProduct(name, effectiveDate);
    }

    @Override
    public PlanPhase findCurrentPhase(final String name) throws CatalogApiException {
        return adapter.findPhase(name, effectiveDate);
    }

    @Override
    public PriceList findCurrentPricelist(final String name) throws CatalogApiException {
        return adapter.findPriceList(name, effectiveDate);
    }

    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return adapter.planChangePolicy(from, to, effectiveDate);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return adapter.planChange(from, to, effectiveDate);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase) throws CatalogApiException {
        return adapter.planCancelPolicy(planPhase, effectiveDate);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier) throws CatalogApiException {
        return adapter.planCreateAlignment(specifier, effectiveDate);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase) throws CatalogApiException {
        return adapter.billingAlignment(planPhase, effectiveDate);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return adapter.planChangeAlignment(from, to, effectiveDate);
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier) throws CatalogApiException {
        return adapter.canCreatePlan(specifier, effectiveDate);
    }

    @Override
    public List<Listing> getAvailableBasePlanListings() throws CatalogApiException {
        //TODO look into this
        return null;
    }

    @Override
    public List<Listing> getAvailableAddOnListings(final String baseProductName, final String priceListName) throws CatalogApiException {
        //TODO look into this
        return null;
    }

    @Override
    public boolean compliesWithLimits(final String phaseName, final String unit, final double value) throws CatalogApiException {
        //TODO look into this
        return false;
    }
}
