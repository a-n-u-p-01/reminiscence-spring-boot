package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendOtpEmail(String toEmail, String otp) {

        try {

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(
                    fromEmail,
                    "Reminiscence"
            );

            helper.setTo(toEmail);

            helper.setSubject("Verify Your Email - Reminiscence");

            helper.setText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                    
                        <div style="max-width: 600px; margin: auto; background: white; padding: 30px; border-radius: 12px;">
                            
                            <h2 style="margin-bottom: 20px;">
                                Verify Your Email
                            </h2>

                            <p>Hello,</p>

                            <p>
                                Thank you for signing up for Reminiscence.
                                Use the OTP below to verify your email address.
                            </p>

                            <div style="text-align: center; margin: 30px 0;">
                                <span style="
                                    font-size: 32px;
                                    font-weight: bold;
                                    letter-spacing: 6px;
                                    padding: 12px 24px;
                                    border-radius: 8px;
                                    background: #f5f5f5;
                                    display: inline-block;">
                                    %s
                                </span>
                            </div>

                            <p>
                                This OTP will expire in <strong>10 minutes</strong>.
                            </p>

                            <p>
                                If you did not request this verification,
                                you can safely ignore this email.
                            </p>

                            <br>

                            <p>
                                Regards,<br>
                                <strong>Reminiscence Team</strong>
                            </p>

                        </div>

                    </body>
                    </html>
                    """.formatted(otp),
                    true
            );

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}