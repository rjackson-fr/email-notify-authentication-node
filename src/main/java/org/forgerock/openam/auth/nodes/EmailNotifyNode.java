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
/**
 * jon.knight@forgerock.com
 *
 * A node that returns true if the user's email address is recorded as breached by the HaveIBeenPwned website (http://haveibeenpwned.com)
 * or false if no breach has been recorded
 */


package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.sm.annotations.adapters.Password;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import com.iplanet.am.util.AMSendMail;
import com.sun.identity.authentication.spi.AuthLoginException;



/**
 * An authentication node to send an email.
 */
@Node.Metadata(outcomeProvider = SingleOutcomeNode.OutcomeProvider.class,
        configClass = EmailNotifyNode.Config.class,
        tags             = {"marketplace"})
public class EmailNotifyNode extends SingleOutcomeNode {

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 100)
        default String attribute() { return "mail"; }
        @Attribute(order = 200)
        default String subject() { return "Subject"; }  
        @Attribute(order = 300)
        default String message() { return "Message"; }
        @Attribute(order = 350)
        default boolean htmlType() { return false; }
        @Attribute(order = 400)
        default String from() { return "admin@forgerock.com"; }
        @Attribute(order = 500)
        default String smtpHostName() { return "localhost"; }
        @Attribute(order = 600)
        default String smtpHostPort() { return "25"; }
        @Attribute(order = 700, requiredValue = false)
        String smtpUserName();
        @Attribute(order = 800)
        @Password
        char[] smtpUserPassword();
        @Attribute(order = 900)
        default boolean smtpSSLEnabled() { return false; }
    }

    private final Config config;
    private final CoreWrapper coreWrapper;
    private final static String NODE_NAME = "EmailNotifyNode";
    private static final Logger debug = LoggerFactory.getLogger(EmailNotifyNode.class);

    /**
     * Guice constructor.
     * @param config The node configuration.
     * @throws NodeProcessException If there is an error reading the configuration.
     */
    @Inject
    public EmailNotifyNode(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.config = config;
        this.coreWrapper = coreWrapper;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        AMIdentity userIdentity = coreWrapper.getIdentity(context.getStateFor(this).get(USERNAME).asString(), context.getStateFor(this).get(REALM).asString());
        String emailAddr = "";

        // Override email "to" field if found in sharedState
        if (context.getStateFor(this).get("email").asString() != null) {
            emailAddr = context.getStateFor(this).get("email").asString();
            debug.error("[" + NODE_NAME + "]: " + "got email address from sharedState: " + emailAddr);
        } else {
            debug.error("[" + NODE_NAME + "]: " + "looking for email address attribute: " + config.attribute());
            try {
                Set<String> idAttrs = userIdentity.getAttribute(config.attribute());
                if (idAttrs == null || idAttrs.isEmpty()) {
                    debug.error("[" + NODE_NAME + "]: " + "unable to find email user attribute: " + config.attribute());
                } else {
                    emailAddr = (String) idAttrs.iterator().next();
                }
            } catch (IdRepoException | SSOException e) {
                debug.error("[" + NODE_NAME + "]: " + "error getting atttibute '{}' ", e);
            }
        }

        // Substitute any variables found in subject and message
        // Strings containing "{{var}}" will be replaced by the content of sharedState "var" if found
        String subject = hydrate(context,config.subject());
        String message = hydrate(context,config.message());
        try {
            debug.error("[" + NODE_NAME + "]: " + "sending email to " + emailAddr);
            sendEmailMessage(config.from(), emailAddr, subject, message);
        } catch (AuthLoginException e) {
            debug.error("[" + NODE_NAME + "]: " + "AuthLoginException exception: " + e);
        }
        return goToNext().build();
    }


    public String hydrate(TreeContext context, String source) {
        String target = "";
        Boolean scanning = true;
        while (scanning) {
            int start = source.indexOf("{{");
            int end = source.indexOf("}}");
            if ((start != -1) && (end != -1)) {
                target = target + source.substring(0,start);
                String variable = source.substring(start+2,end);
                target += context.getStateFor(this).get(variable).asString();
                source = source.substring(end+2,source.length());
            } else {
                target = target + source;
                scanning = false;
            }
        }
        return target;
    }


    /**
     * {@inheritDoc}
     */
    public void sendEmailMessage(String from, String to, String subject, String message) throws AuthLoginException {

        String smtpHostName = config.smtpHostName();
        String smtpHostPort = config.smtpHostPort();
        String smtpUserName = config.smtpUserName();
        String smtpUserPassword = null;
        if (config.smtpUserPassword() != null) {
            smtpUserPassword = String.valueOf(config.smtpUserPassword());
        }
        boolean sslEnabled = config.smtpSSLEnabled();
        boolean startTls = config.smtpSSLEnabled();
            
        // postMail expects an array of recipients
        String tos[] = new String[1];
        tos[0] = to;

        try {
            AMSendMail sendMail = new AMSendMail();

            if (smtpHostName == null || smtpHostPort == null) {
                sendMail.postMail(tos, subject, message, from);
            } else {

                String mimeType = (config.htmlType()) ? "text/html" : "text/plain";
                sendMail.postMail(tos, subject, message, from, mimeType, "UTF-8", smtpHostName,
                        smtpHostPort, smtpUserName, smtpUserPassword,
                        sslEnabled, startTls);

            }
            debug.error("[" + NODE_NAME + "]: " + "sent email to " + to);
        } catch (Exception e) {
            debug.error("[" + NODE_NAME + "]: " + "sendMail exception: " + e);  
            throw new AuthLoginException("Failed to send email to " + to, e);
        }
    }
   
}