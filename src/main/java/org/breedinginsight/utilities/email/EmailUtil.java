/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.utilities.email;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.HttpServerException;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroupDir;
import org.stringtemplate.v4.STRawGroupDir;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

@Singleton
public class EmailUtil {

    @Property(name = "email.relay-server.host")
    private String smtpHostServer;
    @Property(name = "email.relay-server.port")
    private Integer smtpHostPort;
    @Property(name = "email.from")
    private String fromEmail;
    @Property(name = "web.signup.signup.url")
    private String newAccountSignupUrl;
    @Property(name = "web.cookies.account-token")
    private String accountTokenCookieName;

    @Inject
    EmailTemplates emailTemplates;

    private Session getSmtpHost() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHostServer);
        props.put("mail.smtp.port", smtpHostPort);
        props.put("mail.debug", true);
        return Session.getInstance(props, null);
    }

    private void sendEmail(String toEmail, String subject, String body){
        try
        {
            Session session = getSmtpHost();
            MimeMessage msg = new MimeMessage(session);
            //set message headers
            msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
            msg.addHeader("format", "flowed");
            msg.addHeader("Content-Transfer-Encoding", "8bit");

            //TODO: What is the personal attirbute?
            msg.setFrom(new InternetAddress(fromEmail, "NoReply-JD"));
            msg.setReplyTo(InternetAddress.parse(fromEmail, false));

            msg.setSubject(subject, "UTF-8");
            msg.setText(body, "UTF-8");

            msg.setSentDate(new Date());

            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            Transport.send(msg);
            //TODO: Maybe send an email on startup to test our email service is working?
        }
        catch (UnsupportedEncodingException | MessagingException e) {
            throw new HttpServerException(e.getMessage());
        }
    }

    public void sendAccountSignUpEmail(BiUserEntity user, String jwtToken) {

        // Get email template
        ST emailTemplate = emailTemplates.getNewSignupTemplate();

        // Fill in user info
        String signUpUrl = String.format("%s?%s=%s", newAccountSignupUrl, accountTokenCookieName, jwtToken);
        emailTemplate.add("user_name", user.getName());
        emailTemplate.add("new_signup_link", signUpUrl);
        //TODO: Make this not hardcoded
        emailTemplate.add("expiration_time", "1 hour");
        String filledBody = emailTemplate.render();
        String subject = "New Account Sign Up";

        // Send email
        sendEmail(user.getEmail(), subject, filledBody);

    }
}
