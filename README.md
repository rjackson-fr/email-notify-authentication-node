<!--
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
-->
<b>Email Notify Node</b>
<br/>
An authentication node which will send an email.
<br/>
<br/>
<b>Installation</b>
<br/>
Copy the .jar file from the ../target directory into the ../web-container/webapps/openam/WEB-INF/lib directory where AM is deployed.  Restart the web container to pick up the new node.  The node will then appear in the authentication trees components palette.
<br/>
<br/>
<b>Usage</b>
<br/>
This node can be used to send an email message to a user, for example, when used in conjunction with Account Lockout node, to inform the user by email in the change to their account status. If sharedState value of "email" exists then this is used as the recipient email address, otherwise the authenticating user's profile email address is used. Both the email subject and message body can be configured to inject sharedState variables. For example, setting the message body to "Dear {{username}}, Welcome to AM!" will replace {{username}} with the user's real username (if present in sharedState). 
<br/>
<br/>
<b>Suspend</b>
<br/>
The optional feature will allow the node to suspend the journey in the same way as the OOTB email suspend node.  The {{resumeURI}} variable will be made available to the node (and the shared state) so that it can be included in body or subject templates (for the hydrate call)
<br/>
<br/>
<b>To Build</b>
<br/>
Edit the necessary EmailNotifyNode.java as appropriate.  To rebuild, run "mvn clean install" in the directory containing the pom.xml
<br/>
<br/>
<br/>
![ScreenShot](./emailnotify.png)
<br/>
<br/>
<b>Disclaimer</b>
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.

ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.

ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.