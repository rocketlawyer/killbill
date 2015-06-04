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

import java.util.Collection;
import java.util.LinkedList;

import org.joda.time.DateTime;
import org.joda.time.ReadableInstant;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
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
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class PluginCatalogAdapter implements Catalog {

    protected final VersionedPluginCatalog vpc;

    public PluginCatalogAdapter(final VersionedPluginCatalog vpc) {
        super();
        this.vpc = vpc;
    }

    private interface Func<I, O> {

        O apply(final I input);
    }

    private <I, O> Iterable<O> flatMap(final Iterable<I> col, final Func<I, Iterable<O>> mapper) {
        final Collection<O> result = new LinkedList<O>();
        for (final I element : col) {
            result.addAll(Lists.newArrayList(mapper.apply(element)));
        }
        return result;
    }

    private <I, O> Iterable<O> flatMap(final I[] col, final Func<I, Iterable<O>> mapper) {
        return flatMap(Lists.newArrayList(col), mapper);
    }

    private <I, O> Iterable<O> map(final Iterable<I> col, final Func<I, O> mapper) {
        final Collection<O> result = new LinkedList<O>();
        for (final I element : col) {
            result.add(mapper.apply(element));
        }
        return result;
    }

    private <I, O> Iterable<O> map(final I[] col, final Func<I, O> mapper) {
        return map(Lists.newArrayList(col), mapper);
    }

    private <I> Iterable<I> filter(final Iterable<I> col, final Func<I, Boolean> filterFunc) {
        final Collection<I> result = new LinkedList<I>();
        for (final I element : col) {
            if (filterFunc.apply(element)) {
                result.add(element);
            }
        }
        return result;
    }

    private <I> Iterable<I> filter(final I[] col, final Func<I, Boolean> mapper) {
        return filter(Lists.newArrayList(col), mapper);
    }

    private <I> I find(final Iterable<I> col, final Func<I, Boolean> filterFunc) {
        for (final I element : col) {
            if (filterFunc.apply(element)) {
                return element;
            }
        }
        return null;
    }

    private <I> I find(final I[] col, final Func<I, Boolean> mapper) {
        return find(Lists.newArrayList(col), mapper);
    }

    private <I> I find(final Iterable<I> col) {
        return col.iterator().next();
    }

    private <I> I find(final I[] col) {
        return find(Lists.newArrayList(col));
    }

    public Iterable<StandalonePluginCatalog> getActiveCatalogs(final ReadableInstant requestedDate) {
        return filter(vpc.getStandalonePluginCatalogs(), new Func<StandalonePluginCatalog, Boolean>() {
            @Override
            public Boolean apply(final StandalonePluginCatalog input) {
                return input.getEffectiveDate().compareTo(requestedDate) <= 0;
            }
        });
    }

    @Override
    public String getCatalogName() {
        return vpc.getCatalogName();
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) throws CatalogApiException {
        return Iterables.toArray(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Currency>>() {
            @Override
            public Iterable<Currency> apply(final StandalonePluginCatalog input) {
                return input.getCurrencies();
            }
        }), Currency.class);
    }

    @Override
    public Product[] getProducts(final DateTime requestedDate) throws CatalogApiException {
        return Iterables.toArray(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Product>>() {
            @Override
            public Iterable<Product> apply(final StandalonePluginCatalog input) {
                return input.getProducts();
            }
        }), Product.class);
    }

    @Override
    public Plan[] getPlans(final DateTime requestedDate) throws CatalogApiException {
        return Iterables.toArray(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Plan>>() {
            @Override
            public Iterable<Plan> apply(final StandalonePluginCatalog input) {
                return input.getPlans();
            }
        }), Plan.class);
    }

    @Override
    public Plan findPlan(final String name, final DateTime requestedDate) throws CatalogApiException {
        return find(getPlans(requestedDate), new Func<Plan, Boolean>() {
            @Override
            public Boolean apply(final Plan input) {
                return input.getName().equals(name);
            }
        });
    }

    @Override
    public Plan createOrFindPlan(
            final String productName,
            final BillingPeriod billingPeriod,
            final String priceListName,
            final PlanPhasePriceOverridesWithCallContext overrides,
            final DateTime requestedDate
                                ) throws CatalogApiException {
        final PriceList priceList = findPriceList(priceListName, requestedDate);
        final Product product = findProduct(productName, requestedDate);
        Plan plan = find(priceList.getPlans(), new Func<Plan, Boolean>() {
            @Override
            public Boolean apply(final Plan input) {
                return input.getRecurringBillingPeriod().equals(billingPeriod)
                       && input.getProduct().equals(product);
            }
        });
        if (plan == null) {
            //TODO: Make this create a new plan
            plan = plan;
            throw new NotImplementedException();
        }
        return plan;
    }

    @Override
    public Plan findPlan(final String name, final DateTime requestedDate, final DateTime subscriptionStartDate) throws CatalogApiException {
        return find(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Plan>>() {
            @Override
            public Iterable<Plan> apply(final StandalonePluginCatalog input) {
                return input.getPlans();
            }
        }), new Func<Plan, Boolean>() {
            @Override
            public Boolean apply(final Plan input) {
                final Comparable effectiveDateForExistingSubs = new DateTime(input.getEffectiveDateForExistingSubscriptons());
                return input.getName().equals(name) &&
                       effectiveDateForExistingSubs.compareTo(subscriptionStartDate) <= 0;
            }
        });
    }

    @Override
    public Plan createOrFindPlan(
            final String productName,
            final BillingPeriod billingPeriod,
            final String priceListName,
            final PlanPhasePriceOverridesWithCallContext overrides,
            final DateTime requestedDate,
            final DateTime subscriptionStartDate
                                ) throws CatalogApiException {
        final PriceList priceList = findPriceList(priceListName, requestedDate);
        final Product product = findProduct(productName, requestedDate);
        Plan plan = find(priceList.getPlans(), new Func<Plan, Boolean>() {
            @Override
            public Boolean apply(final Plan input) {
                final Comparable effectiveDateForExistingSubs = new DateTime(input.getEffectiveDateForExistingSubscriptons());
                return input.getRecurringBillingPeriod().equals(billingPeriod)
                       && input.getProduct().equals(product)
                       && effectiveDateForExistingSubs.compareTo(subscriptionStartDate) <= 0;
            }
        });
        if (plan == null) {
            plan = plan;
            //TODO: Create the plan
            throw new NotImplementedException();
        }
        return plan;
    }

    @Override
    public Product findProduct(final String name, final DateTime requestedDate) throws CatalogApiException {
        return find(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Product>>() {
                        @Override
                        public Iterable<Product> apply(final StandalonePluginCatalog input) {
                            return input.getProducts();
                        }
                    }), new Func<Product, Boolean>() {
                        @Override
                        public Boolean apply(final Product input) {
                            return input.getName().equals(name);
                        }
                    }
                   );
    }

    @Override
    public PriceList findPriceList(final String name, final DateTime requestedDate) throws CatalogApiException {
        return find(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<PriceList>>() {
            @Override
            public Iterable<PriceList> apply(final StandalonePluginCatalog input) {
                return input.getPriceLists();
            }
        }), new Func<PriceList, Boolean>() {
            @Override
            public Boolean apply(final PriceList input) {
                return input.getName().equals(name);
            }
        });
    }

    public PlanPhase findPhase(final String name, final ReadableInstant requestedDate) {
        return find(flatMap(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Plan>>() {
            @Override
            public Iterable<Plan> apply(final StandalonePluginCatalog input) {
                return input.getPlans();
            }
        }), new Func<Plan, Iterable<PlanPhase>>() {
            @Override
            public Iterable<PlanPhase> apply(final Plan input) {
                return Lists.newArrayList(input.getAllPhases());
            }
        }), new Func<PlanPhase, Boolean>() {
            @Override
            public Boolean apply(final PlanPhase input) {
                return input.getName().equals(name);
            }
        });
    }

    @Override
    public PlanPhase findPhase(final String name, final DateTime requestedDate, final DateTime subscriptionStartDate) throws CatalogApiException {
        return find(flatMap(filter(flatMap(getActiveCatalogs(requestedDate), new Func<StandalonePluginCatalog, Iterable<Plan>>() {
            @Override
            public Iterable<Plan> apply(final StandalonePluginCatalog input) {
                return input.getPlans();
            }
        }), new Func<Plan, Boolean>() {
            @Override
            public Boolean apply(final Plan input) {
                return input.getEffectiveDateForExistingSubscriptons().after(subscriptionStartDate.toDate());
            }
        }), new Func<Plan, Iterable<PlanPhase>>() {
            @Override
            public Iterable<PlanPhase> apply(final Plan input) {
                return Lists.newArrayList(input.getAllPhases());
            }
        }), new Func<PlanPhase, Boolean>() {
            @Override
            public Boolean apply(final PlanPhase input) {
                return input.getName().equals(name);
            }
        });
    }

    public Unit[] getUnits(final ReadableInstant effectiveDate) throws CatalogApiException {
        return Iterables.toArray(flatMap(getActiveCatalogs(effectiveDate), new Func<StandalonePluginCatalog, Iterable<Unit>>() {
            @Override
            public Iterable<Unit> apply(final StandalonePluginCatalog input) {
                return input.getUnits();
            }
        }), Unit.class);
    }

    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier, final DateTime requestedDate) throws CatalogApiException {
        throw new NotImplementedException();
    }
}

