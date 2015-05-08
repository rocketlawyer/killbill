/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
