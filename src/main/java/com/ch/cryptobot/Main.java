package com.ch.cryptobot;

import com.hk.io.IOUtil;
import com.hk.json.Json;
import com.hk.json.JsonValue;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public class Main extends ListenerAdapter
{
	static JDA jda;

	public static void main(String[] args) throws Exception
	{
		jda = JDABuilder.createDefault(readToken(), GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
				.addEventListeners(new Main())
				.setActivity(Activity.playing("Type !ping"))
				.build();

		jda.awaitReady();

		System.out.println("--------------------- DONE ---------------------");
//		jda.getCategoryById(948059969754906634L);

		Guild guild = jda.getGuildById(823326124452478986L);

		for (Member member : guild.loadMembers().get())
		{
			if(!member.getUser().isBot())
				System.out.println(member.getUser().getName());
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event)
	{
		Message msg = event.getMessage();
		if (msg.getContentRaw().equals("!btc"))
		{
			try
			{
				JsonValue value = Json.read(new URL("https://api.coinbase.com/v2/prices/spot?currency=USD"));

				MessageChannel channel = event.getChannel();
				channel.sendMessage(value.getObject().getObject("data").getString("amount")).complete();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	enum Users
	{
		BOOMDEYADA(237732442088275969L),
		D3SMIKE(122510759665205248L),
		DACSUS(705131415305453638L),
		SEPH141(300809109534670849L);

		public final long id;

		Users(long id)
		{
			this.id = id;
		}

		public User getUser(JDA jda)
		{
			return jda.getUserById(id);
		}

		public static User[] getUsers(JDA jda)
		{
			Users[] vals = values();
			User[] users = new User[vals.length];
			for (int i = 0; i < vals.length; i++)
				users[i] = vals[i].getUser(jda);

			return users;
		}
	}

	private static String readToken() throws IOException
	{
		InputStream in = Main.class.getResourceAsStream("/token.txt");
		Reader rdr = new InputStreamReader(Objects.requireNonNull(in));
		String s = IOUtil.readString(rdr);
		rdr.close();
		return s;
	}
}
