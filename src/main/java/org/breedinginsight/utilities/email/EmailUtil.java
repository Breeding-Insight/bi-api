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
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import javax.mail.*;
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
    @Property(name = "email.relay-server.login")
    private String smtpLogin;
    @Property(name = "email.relay-server.password")
    private String smtpPassword;

    private Session getSmtpHost() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHostServer);
        props.put("mail.smtp.port", smtpHostPort);
        props.put("mail.debug", true);
        Authenticator auth = null;
        if (StringUtils.isNotBlank(smtpLogin) && StringUtils.isNotBlank(smtpPassword)) {
            props.put("mail.smtp.auth", true);
            props.put("mail.smtp.ssl.trust", smtpHostServer);
            props.put("mail.smtp.starttls.enable", true);
            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpLogin, smtpPassword);
                }
            };
        }
        return Session.getInstance(props, auth);
    }

    public void sendEmail(String toEmail, String subject, String body){
        try
        {
            Session session = getSmtpHost();
            MimeMessage msg = new MimeMessage(session);
            //set message headers
            msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
            msg.addHeader("format", "flowed");
            msg.addHeader("Content-Transfer-Encoding", "8bit");

            msg.setFrom(new InternetAddress(fromEmail, "NoReply-BI"));
            msg.setReplyTo(InternetAddress.parse(fromEmail, false));

            msg.setSubject(subject, "UTF-8");
            msg.setText(body, "UTF-8");

            msg.setSentDate(new Date());

            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            Transport.send(msg);
        }
        catch (UnsupportedEncodingException | MessagingException e) {
            throw new HttpServerException(e.getMessage());
        }
    }


}
