package org.killbill.billing.catalog.provider;

import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;

import java.util.Set;

public class DefaultCatalogProviderPluginRegistry implements OSGIServiceRegistration<CatalogPluginApi> {
    @Override
    public void registerService(OSGIServiceDescriptor osgiServiceDescriptor, CatalogPluginApi catalogPluginApi) {

    }

    @Override
    public void unregisterService(String s) {

    }

    @Override
    public CatalogPluginApi getServiceForName(String s) {
        return null;
    }

    @Override
    public Set<String> getAllServices() {
        return null;
    }

    @Override
    public Class<CatalogPluginApi> getServiceType() {
        return null;
    }
}
