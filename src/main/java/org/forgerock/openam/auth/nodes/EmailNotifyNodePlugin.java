/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017 ForgeRock AS.
 */
/*
 * jon.knight@forgerock.com
 *
 * Needed to register the node
 */

package org.forgerock.openam.auth.nodes;

import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;
import org.forgerock.openam.plugins.StartupType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Core nodes installed by default with no engine dependencies.
 */
public class EmailNotifyNodePlugin extends AbstractNodeAmPlugin {

    static private String currentVersion = "1.0.5";
    
        /**
         * Specify the Map of list of node classes that the plugin is providing. These will then be installed and
         * registered at the appropriate times in plugin lifecycle.
         *
         * @return The list of node classes.
         */
        @Override
        protected Map<String, Iterable<? extends Class<? extends Node>>> getNodesByVersion() {
            return Collections.singletonMap(EmailNotifyNodePlugin.currentVersion,
                                            Arrays.asList(EmailNotifyNode.class));
        }
    
        /**
         * Handle plugin installation. This method will only be called once, on first AM startup once the plugin
         * is included in the classpath. The {@link #onStartup()} method will be called after this one.
         * <p>
         * No need to implement this unless your AuthNode has specific requirements on install.
         */
        @Override
        public void onInstall() throws PluginException {
            super.onInstall();
        }
    
        /**
         * Handle plugin startup. This method will be called every time AM starts, after {@link #onInstall()},
         * {@link #onAmUpgrade(String, String)} and {@link #upgrade(String)} have been called (if relevant).
         * <p>
         * No need to implement this unless your AuthNode has specific requirements on startup.
         *
         * @param startupType The type of startup that is taking place.
         */
        @Override
        public void onStartup(StartupType startupType) throws PluginException {
            super.onStartup(startupType);
        }
    
        /**
         * This method will be called when the version returned by {@link #getPluginVersion()} is higher than the
         * version already installed. This method will be called before the {@link #onStartup()} method.
         * <p>
         * No need to implement this untils there are multiple versions of your auth node.
         *
         * @param fromVersion The old version of the plugin that has been installed.
         */
        @Override
        public void upgrade(String fromVersion) throws PluginException {
            pluginTools.upgradeAuthNode(EmailNotifyNode.class);
            super.upgrade(fromVersion);
        }
    
        /**
         * The plugin version. This must be in semver (semantic version) format.
         *
         * @return The version of the plugin.
         * @see <a href="https://www.osgi.org/wp-content/uploads/SemanticVersioning.pdf">Semantic Versioning</a>
         */
        @Override
        public String getPluginVersion() {
            return EmailNotifyNodePlugin.currentVersion;
        }
    }