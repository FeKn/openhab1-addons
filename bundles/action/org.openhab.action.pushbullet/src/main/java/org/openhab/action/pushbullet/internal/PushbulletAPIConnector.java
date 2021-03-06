/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.action.pushbullet.internal;

import static org.openhab.action.pushbullet.internal.PushbulletConstants.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.openhab.action.pushbullet.internal.model.Push;
import org.openhab.action.pushbullet.internal.model.PushResponse;
import org.openhab.core.scriptengine.action.ActionDoc;
import org.openhab.core.scriptengine.action.ParamDoc;
import org.openhab.io.net.http.HttpUtil;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * This class contains the methods that are made available in scripts and rules
 * for sending messages via the PushbulletAPIConnector mobile device push service..
 *
 * @author Hakan Tandogan
 * @since 1.11.0
 */
public class PushbulletAPIConnector {

    private static final Logger logger = LoggerFactory.getLogger(PushbulletAPIConnector.class);

    private static final Version VERSION = FrameworkUtil.getBundle(PushbulletAPIConnector.class).getVersion();

    private static final Gson gson = new Gson();

    private static final Map<String, PushbulletBot> bots = new HashMap<String, PushbulletBot>();

    private static final String METHOD = "POST";

    private static final String CONTENT_TYPE = "application/json";

    public static void addBot(String botName, String accessToken) {
        PushbulletBot bot = new PushbulletBot(botName, accessToken);
        bots.put(botName, bot);
    }

    public static int botCount() {
        return bots.size();
    }

    @ActionDoc(text = "Logs bots configured in PushbulletAPIConnector")
    static public boolean logPushbulletBots() {

        logger.info("===========================================================================");

        logger.info("Configured {} bots", botCount());

        for (Map.Entry<String, PushbulletBot> entry : bots.entrySet()) {
            logger.info("  Bot: {}", entry);
        }

        logger.info("===========================================================================");

        return true;
    }

    @ActionDoc(text = "Sends a message to PushbulletAPIConnector")
    public static boolean sendPushbulletNote(
            @ParamDoc(name = "botName", text = "Name of the bot sending the message") String botName,
            @ParamDoc(name = "recipient", text = "recipient of the message") String recipient,
            @ParamDoc(name = "title", text = "title of the message") String title,
            @ParamDoc(name = "message", text = "the message to be sent") String message) {
        logger.debug("Trying to send a note");
        return sendPush(botName, recipient, title, message, "note");
    }

    @ActionDoc(text = "Sends a message to PushbulletAPIConnector from the default bot")
    public static boolean sendPushbulletNote(
            @ParamDoc(name = "recipient", text = "recipient of the message") String recipient,
            @ParamDoc(name = "title", text = "title of the message") String title,
            @ParamDoc(name = "message", text = "the message to be sent") String message) {
        logger.debug("Trying to send a note to the default bot");
        return sendPush(DEFAULT_BOTNAME, recipient, title, message, "note");
    }

    @ActionDoc(text = "Sends a message to PushbulletAPIConnector to all user's devices from default bot")
    public static boolean sendPushbulletNote(
            @ParamDoc(name = "title", text = "title of the message") String title,
            @ParamDoc(name = "message", text = "the message to be sent") String message) {
        logger.debug("Trying to send a note to all user's devices from the default bot");
        return sendPush(DEFAULT_BOTNAME, null, title, message, "note");
    }

    /**
     * Inner method handling all the pushes.
     *
     * @param botName
     * @param recipient
     * @param title
     * @param body
     * @param type
     * @return
     */
    private static boolean sendPush(String botName, String recipient, String title, String body, String type) {
        boolean result = false;
        logger.trace("    Botname is   '{}'", botName);

        PushbulletBot bot = bots.get(botName);
        if (bot == null) {
            logger.warn("Unconfigured pushbullet bot, please check configuration");
            return false;
        }

        logger.trace("    Bot is       '{}'", bot);

        logger.trace("    Recipient is '{}'", recipient);
        logger.trace("    Title is     '{}'", title);
        logger.trace("    Message is   '{}'", body);

        Push push = new Push();
        push.setTitle(title);
        push.setBody(body);
        push.setType(type);

        if (recipient != null) {
            if (isValidEmail(recipient)) {
                logger.trace("    Recipient is an email address");
                push.setEmail(recipient);
            } else if (isValidChannel(recipient)) {
                logger.trace("    Recipient is a channel tag");
                push.setChannel(recipient);
            } else {
                logger.warn("Invalid recipient: {}", recipient);
                logger.warn("Message will be broadcast to all user's devices.");
            }
        }

        logger.trace("     Push: {}", push);

        String request = gson.toJson(push);
        logger.trace("    Packed Request: {}", request);

        try (InputStream stream = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8))) {

            Properties headers = new Properties();
            headers.put("User-Agent", "openHAB / pushbullet action " + VERSION.toString());
            headers.put("Access-Token", bot.getToken());

            String responseString = HttpUtil.executeUrl(METHOD, API_URL_PUSHES, headers, stream, CONTENT_TYPE, TIMEOUT);

            logger.trace("    Got Response: {}", responseString);
            PushResponse response = gson.fromJson(responseString, PushResponse.class);

            logger.trace("    Unpacked Response: {}", response);

            stream.close();

            if ((null != response) && (null == response.getPushError())) {
                result = true;
            }
        } catch (IOException e) {
            logger.warn("IO problems pushing note: {}", e.getMessage());
        }

        return result;

    }

    /**
     * Inner method checking if channel tag is valid.
     *
     * @param channel
     * @return
     */
    private static boolean isValidChannel(String channel) {
        String pattern = "^[a-zA-Z0-9_-]+$";
        return channel.matches(pattern);
    }

    /**
     * Inner method checking if email address is valid.
     *
     * @param email
     * @return
     */
    private static boolean isValidEmail(String email) {
        try {
            InternetAddress emailAddr = new InternetAddress(email);
            emailAddr.validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }
}
