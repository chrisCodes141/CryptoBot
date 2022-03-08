package com.ch.cryptobot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

public class Main extends ListenerAdapter
{
	final JDA jda;
	final Engine engine;

	private Main(Path path) throws IOException, LoginException, InterruptedException
	{
		jda = JDABuilder.createDefault(Util.readToken(), GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
				.addEventListeners(this)
				.setActivity(Activity.watching("crypto"))
				.build();
		jda.awaitReady();

		engine = new Engine(jda, path);

		Guild guild = jda.getGuildById(823326124452478986L);

		for (Member member : Objects.requireNonNull(guild).loadMembers().get())
		{
			if(member.getUser().isBot())
				continue;

			System.out.println(member.getUser().getName());
		}

		System.out.println("--------------------- DONE ---------------------");
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event)
	{
		Message msg = event.getMessage();
		Category cat = msg.getCategory();

		if(cat == null || !"crypto accounts".equalsIgnoreCase(msg.getCategory().getName()))
			return;

		String cmd = msg.getContentRaw();
		if(!cmd.startsWith("!"))
			return;
		cmd = cmd.substring(1);
		if(cmd.trim().isEmpty())
			return;

		String[] args = cmd.split(" ");
		if(args.length > 0)
		{
			cmd = args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}

		engine.run(msg, cmd, args, event.getChannel().getName());
	}

	public static void main(String[] args) throws IOException, LoginException, InterruptedException
	{
		Path path;

		if(args == null || args.length == 0)
		{
			path = Paths.get(System.getProperty("user.dir")).resolve("accounts");
			Files.createDirectories(path);
		}
		else
			path = Paths.get(args[0]);

		new Main(path);

//		Engine engine = new Engine(path);
//
//		Scanner in = new Scanner(System.in);
//
//		System.out.print("\ncmd: ");
//		while(in.hasNextLine())
//		{
//			String cmd = in.nextLine();
//			if(cmd.trim().isEmpty())
//				return;
//
//			args = cmd.split(" ");
//			if(args.length > 0)
//			{
//				cmd = args[0];
//				args = Arrays.copyOfRange(args, 1, args.length);
//			}
//
//			if(cmd.equalsIgnoreCase("exit"))
//				break;
//
//			engine.runCommand(System.out::println, cmd, args, "main");
//
//			System.out.print("\ncmd: ");
//		}
//		System.out.print("\nPeace!\n");
	}
}
