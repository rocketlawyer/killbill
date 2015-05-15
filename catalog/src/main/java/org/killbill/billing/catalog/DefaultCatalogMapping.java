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

package org.killbill.billing.catalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.Fixed;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;

import com.google.common.collect.Iterables;

public class DefaultCatalogMapping {

    public DefaultCatalogMapping() {super(); }

    public static StandaloneCatalog standalonePluginCatalogToStandalongCatalog(
            final StandalonePluginCatalog standalonePluginCatalog,
            final VersionedPluginCatalog vPluginCatalog,
            final VersionedCatalog vc
                                                                              ) throws CatalogApiException {
        final StandaloneCatalog sc = new StandaloneCatalog(standalonePluginCatalog.getEffectiveDate().toDate());
        sc.setCatalogName(vPluginCatalog.getCatalogName())
          .setEffectiveDate(vc.getEffectiveDate())
          .setPlans(plansToDefaultPlans(standalonePluginCatalog.getPlans()))
          .setPriceLists(priceListsToDefaultPriceListSet(standalonePluginCatalog.getPriceLists()))
          .setProducts(productsToDefaultProducts(standalonePluginCatalog.getProducts()))
          .setRecurringBillingMode(vc.getRecurringBillingMode())
          .setSupportedCurrencies(Iterables.toArray(standalonePluginCatalog.getCurrencies(), Currency.class));
        return sc;
    }

    public static <T, K> Iterable<K> map(final Iterable<T> collection, final Mapper<T, K> mapper) {
        final Collection<K> result = new LinkedList<K>();
        for (final T value : collection) {
            result.add(mapper.apply(value));
        }
        return result;
    }

    public static DefaultProduct[] productsToDefaultProducts(final Iterable<Product> products) {
        final Product[] prodArray = Iterables.toArray(products, Product.class);
        final DefaultProduct[] result = new DefaultProduct[prodArray.length];
        for(int i = 0; i < result.length; i++){
            result[i] = productToDefaultProduct(prodArray[i]);
        }
        return result;
    }

    private static DefaultProduct productToDefaultProduct(final Product product) {
        final DefaultProduct dProduct = new DefaultProduct();
        return dProduct.setAvailable(productsToDefaultProducts(Arrays.asList(product.getAvailable())))
                       .setCatagory(product.getCategory())
                       .setIncluded(productsToDefaultProducts(Arrays.asList(product.getIncluded())))
                       .setName(product.getName())
                       .setRetired(product.isRetired());
    }

    public static DefaultPriceListSet priceListsToDefaultPriceListSet(final Iterable<PriceList> priceLists) {
        PriceListDefault pld = null;
        final Iterator<PriceList> priceListIterator = priceLists.iterator();
        final LinkedList<DefaultPriceList> dPriceListList = new LinkedList<DefaultPriceList>();
        while (priceListIterator.hasNext()) {
            final PriceList pList = priceListIterator.next();
            if (pList.getName().equals(PriceListSet.DEFAULT_PRICELIST_NAME)) {
                final DefaultPriceList dpl = priceListToDefaultPriceList(pList);
                pld = new PriceListDefault();
                pld.setName(dpl.getName())
                   .setPlans(dpl.getPlans())
                   .setRetired(dpl.isRetired());
            } else {
                dPriceListList.add(priceListToDefaultPriceList(pList));
            }
        }
        final DefaultPriceList[] result = new DefaultPriceList[dPriceListList.size()];
        dPriceListList.toArray(result);
        return new DefaultPriceListSet(pld, result);
    }

    public static DefaultPriceList priceListToDefaultPriceList(final PriceList pList) {
        final DefaultPriceList dpList = new DefaultPriceList();
        return dpList
                .setName(pList.getName())
                .setPlans(plansToDefaultPlans(Arrays.asList(pList.getPlans())))
                .setRetired(pList.isRetired());
    }

    public static DefaultPlan[] plansToDefaultPlans(final Iterable<Plan> plans) {
        final LinkedList<DefaultPlan> dPlanList = new LinkedList<DefaultPlan>();
        for (final Plan plan : plans) {
            dPlanList.add(planToDefaultPlan(plan));
        }
        final DefaultPlan[] result = new DefaultPlan[dPlanList.size()];
        dPlanList.toArray(result);
        return result;
    }

    public static DefaultPlan planToDefaultPlan(final Plan inputPlan) {
        final DefaultPlan dp = new DefaultPlan();
        dp.setEffectiveDateForExistingSubscriptons(inputPlan.getEffectiveDateForExistingSubscriptons());
        dp.setFinalPhase(planPhaseToDefaultPlanPhase(inputPlan.getFinalPhase(), dp))
          .setInitialPhases(planPhasesToDefaultPlanPhases(inputPlan.getInitialPhases(), dp))
          .setName(inputPlan.getName())
          .setPlansAllowedInBundle(inputPlan.getPlansAllowedInBundle())
          .setProduct(productToDefaultProduct(inputPlan.getProduct()));
        return dp;
    }

    public static DefaultPlanPhase[] planPhasesToDefaultPlanPhases(final PlanPhase[] inputPlanPhases, final DefaultPlan dPlan) {
        final DefaultPlanPhase[] result = new DefaultPlanPhase[inputPlanPhases.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = planPhaseToDefaultPlanPhase(inputPlanPhases[i], dPlan);
        }
        return result;
    }

    public static DefaultPlanPhase planPhaseToDefaultPlanPhase(final PlanPhase inputPlanPhase, final DefaultPlan dPlan) {
        final DefaultPlanPhase dpPhase = new DefaultPlanPhase();
        return dpPhase.setDuration(durationToDefaultDuration(inputPlanPhase.getDuration()))
                      .setFixed(fixedToDefaultFixed(inputPlanPhase.getFixed(), dpPhase))
                      .setPhaseType(inputPlanPhase.getPhaseType())
                      .setPlan(dPlan)
                      .setRecurring(recurringToDefaultRecurring(
                                            recurringToDefaultRecurring(inputPlanPhase.getRecurring(), dpPhase, dPlan),
                                            dpPhase,
                                            dPlan
                                                               )
                                   )
                      .setUsages(usagesToDefaultUsages(inputPlanPhase.getUsages(), dpPhase));

    }

    public static DefaultUsage[] usagesToDefaultUsages(final Usage[] usages, final DefaultPlanPhase dpPhase) {
        final DefaultUsage[] dUsages = new DefaultUsage[usages.length];
        for (int i = 0; i < usages.length; i++) {
            dUsages[i] = usageToDefaultUsage(usages[i], dpPhase);
        }
        return dUsages;
    }

    private static DefaultUsage usageToDefaultUsage(final Usage usage1, final DefaultPlanPhase dpPhase) {
        final DefaultUsage dUsage = new DefaultUsage();
        dUsage.setBillingMode(usage1.getBillingMode())
              .setBillingPeriod(usage1.getBillingPeriod())
              .setBlocks(blocksToDefaultBlocks(usage1.getBlocks(), dpPhase))
              .setFixedPrice(priceToDefaultPrice(usage1.getFixedPrice()))
              .setLimits(limitsToDefaultLimits(usage1.getLimits()))
              .setName(usage1.getName())
              .setPhase(dpPhase)
              .setRecurringPrice(priceToDefaultPrice(usage1.getRecurringPrice()))
              .setTiers(tiersToDefaultTiers(usage1.getTiers(), dpPhase, usage1.getUsageType()))
              .setUsageType(usage1.getUsageType());
        return dUsage;
    }

    public static DefaultTier[] tiersToDefaultTiers(final Tier[] tiers, final DefaultPlanPhase dpPhase, final UsageType usageType) {
        final DefaultTier[] defaultTiers = new DefaultTier[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            final Tier tier = tiers[i];
            defaultTiers[i] = tierToDefaultTier(tier, usageType, dpPhase);
        }
        return defaultTiers;
    }

    private static DefaultTier tierToDefaultTier(final Tier tier, final UsageType usageType, final DefaultPlanPhase dpPhase) {
        final DefaultTier result = new DefaultTier();
        result.setBlocks(tieredBlocksToDefaultTieredBlocks(tier.getTieredBlocks()))
              .setFixedPrice(priceToDefaultPrice(tier.getFixedPrice()))
              .setLimits(limitsToDefaultLimits(tier.getLimits()))
              .setPhase(dpPhase)
              .setRecurringPrice(priceToDefaultPrice(tier.getRecurringPrice()));
        result.setUsageType(usageType);
        return result;
    }

    public static DefaultTieredBlock[] tieredBlocksToDefaultTieredBlocks(final TieredBlock[] tieredBlocks) {
        final DefaultTieredBlock[] defaultTieredBlocks = new DefaultTieredBlock[tieredBlocks.length];
        for (int i = 0; i < tieredBlocks.length; i++) {
            final TieredBlock tBlock = tieredBlocks[i];
            defaultTieredBlocks[i] = new DefaultTieredBlock().setMax(tBlock.getMax()).setType(tBlock.getType());
        }
        return defaultTieredBlocks;
    }

    public static DefaultLimit[] limitsToDefaultLimits(final Limit[] limits) {
        final DefaultLimit[] defaultLimits = new DefaultLimit[limits.length];
        for (int i = 0; i < limits.length; i++) {
            final Limit limit = limits[i];
            defaultLimits[i] = new DefaultLimit()
                    .setMax(limit.getMax())
                    .setMin(limit.getMin())
                    .setUnit(unitToDefaultUnit(limit.getUnit()));
        }
        return defaultLimits;
    }

    public static DefaultBlock[] blocksToDefaultBlocks(final Block[] blocks, final DefaultPlanPhase dpPhase) {
        final DefaultBlock[] dBlocks = new DefaultBlock[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            final Block block = blocks[i];
            dBlocks[i] = new DefaultBlock()
                    .setPhase(dpPhase)
                    .setPrices(priceToDefaultPrice(block.getPrice()))
                    .setSize(block.getSize())
                    .setType(block.getType())
                    .setUnit(unitToDefaultUnit(block.getUnit()));
        }
        return dBlocks;
    }

    public static DefaultUnit unitToDefaultUnit(final Unit unit) {
        final DefaultUnit dUnit = new DefaultUnit();
        return dUnit.setName(unit.getName());
    }

    public static DefaultRecurring recurringToDefaultRecurring(final Recurring inputRecurring, final DefaultPlanPhase dpPhase, final DefaultPlan dPlan) {
        final DefaultRecurring dRecurring = new DefaultRecurring();
        return dRecurring.setBillingPeriod(inputRecurring.getBillingPeriod())
                         .setPhase(dpPhase)
                         .setPlan(dPlan)
                         .setRecurringPrice(priceToDefaultPrice(inputRecurring.getRecurringPrice()));
    }

    public static DefaultDuration durationToDefaultDuration(final Duration inputDuration) {
        final DefaultDuration dDuration = new DefaultDuration();
        return dDuration.setNumber(inputDuration.getNumber())
                        .setUnit(inputDuration.getUnit());
    }

    public static DefaultFixed fixedToDefaultFixed(final Fixed inputFixed, final DefaultPlanPhase dpPhase) {
        final DefaultFixed dFixed = new DefaultFixed();
        return dFixed.setFixedPrice(priceToDefaultPrice(inputFixed.getPrice()))
                     .setPhase(dpPhase)
                     .setType(inputFixed.getType());
    }

    public static DefaultInternationalPrice priceToDefaultPrice(final InternationalPrice inputPrice) {
        final DefaultInternationalPrice diPrice = new DefaultInternationalPrice();
        return diPrice.setPrices(priceToDefaultPrice(inputPrice.getPrices()));
    }

    public static DefaultPrice[] priceToDefaultPrice(final Price[] prices) {
        final DefaultPrice[] dPriceList = new DefaultPrice[prices.length];
        for (int i = 0; i < prices.length; i++) {
            try {
                dPriceList[i] = new DefaultPrice(prices[i].getValue(), prices[i].getCurrency());
            } catch (final CurrencyValueNull currencyValueNull) {
                dPriceList[i] = new DefaultPrice(BigDecimal.ZERO, prices[i].getCurrency());
            }
        }
        return dPriceList;
    }

    public interface Mapper<T, K> {

        K apply(T input);
    }
}