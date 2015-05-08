package org.killbill.billing.catalog.glue;

import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.catalog.provider.DefaultCatalogProviderPluginRegistry;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

import com.google.inject.Inject;
import com.google.inject.Provider;


public class DefaultCatalogProviderPluginRegistryProvider implements Provider<OSGIServiceRegistration<CatalogPluginApi>> {
    @Inject
    public DefaultCatalogProviderPluginRegistryProvider(){
    }

    @Override
    public OSGIServiceRegistration<CatalogPluginApi> get() {
        return new DefaultCatalogProviderPluginRegistry();
    }
}
