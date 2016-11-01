/*
 * Copyright 2016 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.acosix.alfresco.mtsupport.repo.subsystems;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.management.subsystems.AbstractPropertyBackedBean;
import org.alfresco.repo.management.subsystems.DefaultChildApplicationContextManager;
import org.alfresco.repo.management.subsystems.PropertyBackedBeanState;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

/**
 * Instances of this class allow client code to identity the specific ID of a subsystem instance.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class SubsystemChildApplicationContextManager extends DefaultChildApplicationContextManager
{

    /** The default type name. */
    protected String defaultTypeName;

    /** The default chain. */
    protected String defaultChain;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaultTypeName(final String defaultTypeName)
    {
        super.setDefaultTypeName(defaultTypeName);
        this.defaultTypeName = defaultTypeName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaultChain(final String defaultChain)
    {
        super.setDefaultChain(defaultChain);
        this.defaultChain = defaultChain;
    }

    /**
     * Determines the instance ID for a specific child application context from within all the child application contexts managed by this
     * instance.
     *
     * @param childApplicationContext
     *            the child application context
     * @return the ID of the child application instance or {@code null} if none of the currently active child application contexts match the
     *         provided one
     */
    public String determineInstanceId(final ApplicationContext childApplicationContext)
    {
        this.lock.readLock().lock();
        try
        {
            final SubsystemApplicationContextManagerState state = (SubsystemApplicationContextManagerState) this.getState(false);

            final Collection<String> instanceIds = state.getInstanceIds();
            final AtomicReference<String> matchingInstanceId = new AtomicReference<>(null);

            for (final String id : instanceIds)
            {
                if (matchingInstanceId.get() == null)
                {
                    final SubsystemChildApplicationContextFactory applicationContextFactory = state.getApplicationContextFactory(id);
                    final ApplicationContext readOnlyApplicationContext = applicationContextFactory.getReadOnlyApplicationContext();

                    if (readOnlyApplicationContext == childApplicationContext)
                    {
                        matchingInstanceId.set(id);
                    }
                }
            }

            return matchingInstanceId.get();
        }
        finally

        {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Looks up the default subsystem configuration properties files for the provided subsystem instance.
     *
     * @param instanceId
     *            the ID of the subsystem instanceof for which to look up the default configuration properties files
     * @return the list of default configuration properties files
     */
    public Resource[] getSubsystemDefaultPropertiesResources(final String instanceId)
    {
        this.lock.readLock().lock();
        try
        {
            final SubsystemApplicationContextManagerState state = (SubsystemApplicationContextManagerState) this.getState(false);
            final SubsystemChildApplicationContextFactory applicationContextFactory = state.getApplicationContextFactory(instanceId);

            final StringBuilder propertiesLocationBuilder = new StringBuilder();
            propertiesLocationBuilder.append("classpath*:alfresco");
            propertiesLocationBuilder.append("/subsystems/");
            propertiesLocationBuilder.append(this.getCategory());
            propertiesLocationBuilder.append('/');
            propertiesLocationBuilder.append(applicationContextFactory.getTypeName());
            propertiesLocationBuilder.append("/*.properties");

            final String basePropertiesPattern = propertiesLocationBuilder.toString();
            final Resource[] resources = this.getParent().getResources(basePropertiesPattern);
            return resources;
        }
        catch (final IOException ioex)
        {
            throw new AlfrescoRuntimeException("Error loading resources", ioex);
        }
        finally

        {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Looks up the extension subsystem configuration properties files for the provided subsystem instance.
     *
     * @param instanceId
     *            the ID of the subsystem instanceof for which to look up the extension configuration properties files
     * @return the list of extension configuration properties files
     */
    public Resource[] getSubsystemExtensionPropertiesResources(final String instanceId)
    {
        this.lock.readLock().lock();
        try
        {
            final SubsystemApplicationContextManagerState state = (SubsystemApplicationContextManagerState) this.getState(false);
            final SubsystemChildApplicationContextFactory applicationContextFactory = state.getApplicationContextFactory(instanceId);

            final List<String> idList = applicationContextFactory.getId();

            final StringBuilder propertiesLocationBuilder = new StringBuilder();
            propertiesLocationBuilder.append("classpath*:alfresco");
            propertiesLocationBuilder.append("/extension");
            propertiesLocationBuilder.append("/subsystems/");
            propertiesLocationBuilder.append(this.getCategory());
            propertiesLocationBuilder.append('/');
            propertiesLocationBuilder.append(applicationContextFactory.getTypeName());
            propertiesLocationBuilder.append("/");
            propertiesLocationBuilder.append(idList.get(idList.size() - 1));
            propertiesLocationBuilder.append("/*.properties");

            final String extensionPropertiesPattern = propertiesLocationBuilder.toString();
            final Resource[] resources = this.getParent().getResources(extensionPropertiesPattern);
            return resources;
        }
        catch (final IOException ioex)
        {
            throw new AlfrescoRuntimeException("Error loading resources", ioex);
        }
        finally

        {
            this.lock.readLock().unlock();
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected PropertyBackedBeanState createInitialState() throws IOException
    {
        return new SubsystemApplicationContextManagerState(this.defaultChain, this.defaultTypeName);
    }

    /**
     *
     * This class exists primarily because the default {@link ApplicationContextManagerState} simply cannot be extended in any way due to
     * excessive use of private visibility. Most of its implementation had to be copied verbatim from the base class but due to instance-of
     * requirements it was impossible to drop the class inheritance hierarchy.
     *
     * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
     */
    protected class SubsystemApplicationContextManagerState extends ApplicationContextManagerState
    {

        private static final String PROPERTY_CHAIN = "chain";

        /** The instance ids. */
        protected final List<String> instanceIds = new ArrayList<>(10);

        /** The child application contexts. */
        protected final Map<String, SubsystemChildApplicationContextFactory> childApplicationContexts = new TreeMap<>();

        /** The default type name. */
        protected final String defaultTypeName;

        protected SubsystemApplicationContextManagerState(final String defaultChain, final String defaultTypeName)
        {
            super(defaultChain, defaultTypeName);

            // Work out what the default type name should be; either specified explicitly or implied by the first member
            // of the default chain
            if (SubsystemChildApplicationContextManager.this.defaultChain != null
                    && SubsystemChildApplicationContextManager.this.defaultChain.length() > 0)
            {
                // Use the first type as the default, unless one is specified explicitly
                if (defaultTypeName == null)
                {
                    this.updateChain(defaultChain, AbstractPropertyBackedBean.DEFAULT_INSTANCE_NAME);
                    this.defaultTypeName = this.childApplicationContexts.get(this.instanceIds.get(0)).getTypeName();
                }
                else
                {
                    this.defaultTypeName = defaultTypeName;
                    this.updateChain(defaultChain, defaultTypeName);
                }
            }
            else if (defaultTypeName == null)
            {
                this.defaultTypeName = AbstractPropertyBackedBean.DEFAULT_INSTANCE_NAME;
            }
            else
            {
                this.defaultTypeName = defaultTypeName;
            }
        }

        @Override
        public String getProperty(final String name)
        {
            if (!name.equals(PROPERTY_CHAIN))
            {
                return null;
            }
            return this.getChainString();
        }

        @Override
        public void setProperty(final String name, final String value)
        {
            super.setProperty(name, value);

            if (name.equals(PROPERTY_CHAIN))
            {
                this.updateChain(value, this.defaultTypeName);
            }
        }

        @Override
        public ApplicationContext getApplicationContext(final String id)
        {
            final SubsystemChildApplicationContextFactory child = this.childApplicationContexts.get(id);
            return child == null ? null : child.getApplicationContext();
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        protected SubsystemChildApplicationContextFactory getApplicationContextFactory(final String id)
        {
            return this.childApplicationContexts.get(id);
        }

        protected String getChainString()
        {
            final StringBuilder orderString = new StringBuilder(100);
            for (final String id : new ArrayList<>(this.instanceIds))
            {
                if (orderString.length() > 0)
                {
                    orderString.append(",");
                }
                orderString.append(id).append(':').append(this.childApplicationContexts.get(id).getTypeName());
            }
            return orderString.toString();
        }

        protected void updateChain(final String orderString, final String defaultTypeName)
        {
            try
            {
                final StringTokenizer tkn = new StringTokenizer(orderString, ", \t\n\r\f");
                final List<String> newInstanceIds = new ArrayList<>(tkn.countTokens());
                while (tkn.hasMoreTokens())
                {
                    final String instance = tkn.nextToken();
                    final int sepIndex = instance.indexOf(':');
                    final String id = sepIndex == -1 ? instance : instance.substring(0, sepIndex);
                    final String typeName = sepIndex == -1 || sepIndex + 1 >= instance.length() ? defaultTypeName
                            : instance.substring(sepIndex + 1);
                    newInstanceIds.add(id);

                    // Look out for new or updated children
                    SubsystemChildApplicationContextFactory factory = this.childApplicationContexts.get(id);

                    // If we have the same instance ID but a different type, treat that as a destroy and remove
                    if (factory != null && !factory.getTypeName().equals(typeName))
                    {
                        factory.lockWrite();
                        ;
                        try
                        {
                            factory.destroy(true);
                        }
                        finally
                        {
                            factory.unlockWrite();
                        }
                        factory = null;
                    }
                    if (factory == null)
                    {
                        // Generate a unique ID within the category
                        final List<String> childId = new ArrayList<>(2);
                        childId.add("managed");
                        childId.add(id);
                        this.childApplicationContexts.put(id,
                                new SubsystemChildApplicationContextFactory(SubsystemChildApplicationContextManager.this.getParent(),
                                        SubsystemChildApplicationContextManager.this.getRegistry(),
                                        SubsystemChildApplicationContextManager.this.getPropertyDefaults(),
                                        SubsystemChildApplicationContextManager.this.getCategory(), typeName, childId));
                    }
                }

                // Destroy any children that have been removed
                final Set<String> idsToRemove = new TreeSet<>(this.childApplicationContexts.keySet());
                idsToRemove.removeAll(newInstanceIds);
                for (final String id : idsToRemove)
                {
                    final SubsystemChildApplicationContextFactory factory = this.childApplicationContexts.remove(id);
                    factory.lockWrite();
                    try
                    {
                        factory.destroy(true);
                    }
                    finally
                    {
                        factory.unlockWrite();
                    }
                }
                this.instanceIds.clear();
                this.instanceIds.addAll(newInstanceIds);
            }
            catch (final RuntimeException e)
            {
                throw e;
            }
            catch (final Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}