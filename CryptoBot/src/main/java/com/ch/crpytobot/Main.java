package com.ch.crpytobot;

import com.hk.json.Json;
import com.hk.json.JsonValue;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class Main extends ListenerAdapter {
    static JDA jda;
    public static void main(String[] args) throws LoginException, IOException {
        // args[0] should be the token
        // We only need 2 intents in this bot. We only respond to messages in guilds and private channels.
        // All other events will be disabled.
        jda = JDABuilder.createDefault("OTQ3OTY0MzY3NDU3MDk5ODI2.Yh06nQ.NMPs6GkKiqDbO2gKCS4J6iOoQ8E")
                .addEventListeners(new Main())
                .setActivity(Activity.playing("Type !ping"))
                .build();

    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        Message msg = event.getMessage();
        if (msg.getContentRaw().equals("!ping"))
        {
            MessageChannel channel = event.getChannel();
            long time = System.currentTimeMillis();
            channel.sendMessage("Pong!") /* => RestAction<Message> */
                    .queue(response /* => Message */ -> {
                        response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();
                    });
        }

        if (msg.getContentRaw().equals("!btc"))
        {
            try {
                JsonValue value = Json.read(new URL("https://api.coinbase.com/v2/prices/spot?currency=USD"));


            MessageChannel channel = event.getChannel();
            long time = System.currentTimeMillis();
            channel.sendMessage(value.getObject().getObject("data").getString("amount")).complete();
        } catch (IOException e) {
        e.printStackTrace();
    }
        }
    }

}
