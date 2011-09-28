/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.environment.osgi.impl.extension.beans;

import org.jboss.weld.environment.osgi.api.BundleState;
import org.jboss.weld.environment.osgi.api.Registration;
import org.jboss.weld.environment.osgi.api.Service;
import org.jboss.weld.environment.osgi.api.ServiceRegistry;
import org.jboss.weld.environment.osgi.api.annotation.Filter;
import org.jboss.weld.environment.osgi.api.events.AbstractServiceEvent;
import org.jboss.weld.environment.osgi.api.events.BundleContainerEvents;
import org.jboss.weld.environment.osgi.api.events.BundleEvents.BundleInvalid;
import org.jboss.weld.environment.osgi.api.events.ServiceEvents;
import org.jboss.weld.environment.osgi.api.events.BundleEvents.BundleValid;
import org.jboss.weld.environment.osgi.impl.extension.service.WeldOSGiExtension;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.weld.environment.osgi.impl.annotation.FilterAnnotation;
import org.jboss.weld.environment.osgi.impl.annotation.SpecificationAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ServiceRegistry}.
 *
 * @author Mathieu ANCELIN - SERLI (mathieu.ancelin@serli.com)
 * @author Matthieu CLOCHARD - SERLI (matthieu.clochard@serli.com)
 */
@ApplicationScoped
public class ServiceRegistryImpl implements ServiceRegistry {

    private static final Logger logger =
                                LoggerFactory.getLogger(ServiceRegistryImpl.class);

    @Inject
    private BundleContext registry;

    @Inject
    private Bundle bundle;

    @Inject
    private Instance<Object> instances;

    @Inject
    private RegistrationsHolderImpl holder;

    @Inject
    private BeanManager manager;

    @Inject
    private Event<BundleValid> validEvent;

    @Inject
    private Event<BundleInvalid> invalidEvent;

    @Inject
    private Event<ServiceEvents.ServiceAvailable> availableEvent;

    @Inject
    private Event<ServiceEvents.ServiceUnavailable> unavailableEvent;

    @Inject
    private WeldOSGiExtension extension;

    @Inject
    private BundleHolder bundleHolder;

    private Map<Class, Set<Filter>> osgiServiceDependencies;

    private Map<Class<?>, Beantype<?>> types =
                                       new HashMap<Class<?>, Beantype<?>>();

    @Override
    public <T> Registration<T> registerService(Class<T> contract,
                                               Class<? extends T> implementation) {
        ServiceRegistration reg = registry.registerService(contract.getName(),
                                                           instances.select(implementation).get(),
                                                           null);
        holder.addRegistration(reg);
        return new RegistrationImpl<T>(contract, registry, bundle, holder);
    }

    @Override
    public <T, U extends T> Registration<T> registerService(Class<T> contract,
                                                            U implementation) {
        ServiceRegistration reg = registry.registerService(contract.getName(),
                                                           implementation,
                                                           null);
        holder.addRegistration(reg);
        return new RegistrationImpl<T>(contract, registry, bundle, holder);
    }

    @Override
    public <T> Service<T> getServiceReferences(Class<T> contract) {
        return new ServiceImpl<T>(contract, registry);
    }

    @PreDestroy
    public void stop() {
        for (Beantype<?> type : types.values()) {
            type.destroy();
        }
    }

    public <T> void registerNewType(Class<T> type) {
        if (!types.containsKey(type)) {
            types.put(type, new Beantype<T>(type, manager));
        }
    }

    public void listenStartup(@Observes BundleContainerEvents.BundleContainerInitialized event) {
        osgiServiceDependencies = extension.getRequiredOsgiServiceDependencies();
        checkForValidDependencies(null);
    }

    public void bind(@Observes ServiceEvents.ServiceArrival arrival) {
        checkForValidDependencies(arrival);
    }

    public void changed(@Observes ServiceEvents.ServiceChanged changed) {
        checkForValidDependencies(changed);
    }

    public void unbind(@Observes ServiceEvents.ServiceDeparture departure) {
        checkForValidDependencies(departure);
    }

    private void checkForValidDependencies(AbstractServiceEvent event) {
        logger.trace("Entering ServiceRegistryImpl : "
                     + "checkForValidDependencies() with parameter {} "
                     + "for bundle {} and instance {}",
                     new Object[] {event, bundle, this});
        if (event == null || applicable(event.getServiceClasses(getClass()))) {
            boolean valid = true;
            if (!osgiServiceDependencies.isEmpty()) {
                for (Map.Entry<Class, Set<Filter>> entry : osgiServiceDependencies.entrySet()) {
                    Class clazz = entry.getKey();
                    logger.trace("Scanning for {}", clazz);
                    ServiceReference[] refs = null;
                    for (Filter filter : entry.getValue()) {
                        logger.trace("Scanning for filter {}", filter);
                        boolean available = true;
                        try {
                            if (filter != null
                                && filter.value() != null
                                && filter.value().length() > 0) {
                                refs = registry.getServiceReferences(clazz.getName(),
                                                                     filter.value());
                            } else {
                                refs = registry.getServiceReferences(clazz.getName(),
                                                                     null);
                            }
                            if (refs != null) {
                                if (refs.length <= 0) {
                                    valid = false;
                                    available = false;
                                }
                            } else {
                                valid = false;
                                available = false;
                            }
                        }
                        catch(InvalidSyntaxException ex) {
                            valid = false;
                            available = false;
                        }
                        if (available) {
                            logger.debug("Service {} with filter {} is available", clazz, filter);
                            Event e = availableEvent.select(new SpecificationAnnotation(clazz));
                            ServiceEvents.ServiceAvailable msg = new ServiceEvents.ServiceAvailable(refs[0], registry);
                            if (filter != null) {
                                e = e.select(new FilterAnnotation(filter.value()));
                            }
                            e.fire(msg);
                        } else {
                            logger.debug("Service {} with filter {} is unavailable", clazz, filter);
                            Event e = unavailableEvent.select(new SpecificationAnnotation(clazz));
                            ServiceReference ref = null;
                            if (event != null) {
                                ref = event.getReference();
                            }
                            ServiceEvents.ServiceUnavailable msg = new ServiceEvents.ServiceUnavailable(ref, registry);
                            if (filter != null) {
                                e = e.select(new FilterAnnotation(filter.value()));
                            }
                            e.fire(msg);
                        }
                        refs = null;
                    }
                }
            }
            // TODO : synchronize here to change the state of the bundle
            if (valid && bundleHolder.getState().equals(BundleState.INVALID)) {
                logger.debug("Bundle {} is now VALID", bundleHolder.getBundle());
                bundleHolder.setState(BundleState.VALID);
                validEvent.fire(new BundleValid(bundle));
            } else if (!valid && (bundleHolder.getState().equals(BundleState.VALID) || event == null)) {
                logger.debug("Bundle {} is now INVALID", bundleHolder.getBundle());
                bundleHolder.setState(BundleState.INVALID);
                invalidEvent.fire(new BundleInvalid(bundle));
            }
        }
    }

    private boolean applicable(List<Class<?>> classes) {
        logger.trace("Entering ServiceRegistryImpl : "
                     + "applicable() with parameter {} "
                     + "for bundle {} and instance {}",
                     new Object[] {classes, bundle, this});
        for (Class<?> clazz : classes) {
            if (osgiServiceDependencies.containsKey(clazz)) {
                logger.trace("Class {} is required", clazz);
                return true;
            }
        }
        return false;
    }

    private class Beantype<T> implements Provider<T> {

        private final Class<T> clazz;

        private final BeanManager manager;

        private final AnnotatedType annoted;

        private final InjectionTarget it;

        private final CreationalContext<?> cc;

        private Collection<T> instances = new ArrayList<T>();

        public Beantype(Class<T> clazz, BeanManager manager) {
            this.clazz = clazz;
            this.manager = manager;
            annoted = manager.createAnnotatedType(clazz);
            it = manager.createInjectionTarget(annoted);
            cc = manager.createCreationalContext(null);
        }

        public void destroy() {
            for (T instance : instances) {
                it.preDestroy(instance);
                it.dispose(instance);
            }
            cc.release();
        }

        @Override
        public T get() {
            T instance = (T) it.produce(cc);
            it.inject(instance, cc);
            it.postConstruct(instance);
            instances.add(instance);
            return instance;
        }

    }
}
