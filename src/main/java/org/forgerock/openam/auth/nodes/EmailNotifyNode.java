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
import static org.forgerock.openam.auth.node.api.Action.suspend;
import static org.forgerock.openam.auth.node.api.SuspendedTextOutputCallback.info;
import com.iplanet.am.util.AMSendMail;
import com.sun.identity.authentication.spi.AuthLoginException;
import java.net.URI;


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
        @Attribute(order = 800, requiredValue = false)
        @Password 
        char[] smtpUserPassword();
        @Attribute(order = 900)
        default boolean smtpSSLEnabled() { return false; }
        @Attribute(order = 950)
        default boolean suspendEnabled() { return false; }
    }

    static final String RESUME_URI = "resumeURI";
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
        if (context.hasResumedFromSuspend()) {
            debug.debug("[" + NODE_NAME + "]: " + "resumed suspended journey");
            return goToNext().build();
        }
        AMIdentity userIdentity = coreWrapper.getIdentity(context.sharedState.get(USERNAME).asString(), context.sharedState.get(REALM).asString());
        String emailAddr = "";

        // Override email "to" field if found in sharedState
        if (context.sharedState.get("email").asString() != null) {
            emailAddr = context.sharedState.get("email").asString();
            debug.debug("[" + NODE_NAME + "]: " + "got email address from sharedState: " + emailAddr);
        } else {
            debug.debug("[" + NODE_NAME + "]: " + "looking for email address attribute: " + config.attribute());
            try {
                Set idAttrs = userIdentity.getAttribute(config.attribute());
                if (idAttrs == null || idAttrs.isEmpty()) {
                    debug.error("[" + NODE_NAME + "]: " + "unable to find email user attribute: " + config.attribute());
                } else {
                    emailAddr = (String) idAttrs.iterator().next();
                }
            } catch (IdRepoException | SSOException e) {
                debug.error("[" + NODE_NAME + "]: " + "error getting atttibute '{}' ", e);
            }
        }

        // create finals in order to call lambda
        final String to = emailAddr;
        final String from = config.from();
        final String subject = config.subject();
        final String message = config.message();

        try {
            debug.debug("[" + NODE_NAME + "]: " + "sending email to " + emailAddr);
            if (config.suspendEnabled()) {
                debug.debug("[" + NODE_NAME + "]: " + "suspending journey after email sent");
                return suspend(resumeURI -> createSuspendOutcome(context, resumeURI, from, to, subject, message)).build();
            } else {
                sendEmailMessage(context, from, to, subject, message);
            }
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
                target += context.sharedState.get(variable).asString();
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
    public void sendEmailMessage(TreeContext context, String from, String to, String subjectTemplate, String messageTemplate) throws AuthLoginException {
        // Substitute any variables found in subject and message
        // Strings containing "{{var}}" will be replaced by the content of sharedState "var" if found
        String subject = hydrate(context,subjectTemplate);
        String message = hydrate(context,messageTemplate);

        String smtpHostName = config.smtpHostName();
        String smtpHostPort = config.smtpHostPort();
        String smtpUserName = config.smtpUserName();
        String smtpUserPassword = null;
        if (config.smtpUserPassword() != null) {
            smtpUserPassword = String.valueOf(config.smtpUserPassword());
        }
        boolean sslEnabled = config.smtpSSLEnabled();
        boolean startTls = config.smtpSSLEnabled();
        boolean suspendEnabled = config.suspendEnabled();
            
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
            debug.debug("[" + NODE_NAME + "]: " + "sent email to " + to);
        } catch (Exception e) {
            debug.error("[" + NODE_NAME + "]: " + "sendMail exception: " + e);  
            throw new AuthLoginException("Failed to send email to " + to, e);
        }
    }

    private SuspendedTextOutputCallback createSuspendOutcome(TreeContext context, URI resumeURI,
            String from, String to, String subject, String message)  {

        String output = "An email has been sent to your inbox.";
        debug.debug("[" + NODE_NAME + "]: " + "resumeURI: " + resumeURI);
        context.sharedState.put(RESUME_URI, resumeURI.toString());
        try {
            sendEmailMessage(context, from, to, subject, message);
        } catch (AuthLoginException e) {
            output = "Error sending email";
        }
        return info(output);
    }
   
}