package com.ch.cryptobot;

import com.hk.json.*;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

class Accounts
{
	final Engine engine;
	final Path accDir;
	private final Map<String, Long> accountMap;
	private final Map<String, Long> channelMap;

	Accounts(Engine engine, Path path) throws FileNotFoundException
	{
		this.engine = engine;
		accDir = path;
		accountMap = new HashMap<>();
		channelMap = new HashMap<>();
		load();

		System.out.println("Using Account Directory: " + accDir);
	}

	public Set<String> getAccounts()
	{
		return accountMap.keySet();
	}

	public String getInvalidMessage(String name)
	{
		if(!name.matches("[0-9a-zA-Z\\-]+"))
			return "_Only letters, numbers, and hyphens allowed!_";
		else if (name.isEmpty())
			return "_Name can't be empty!_";
		else if (name.length() > 16)
			return "_Name is " + (16 - name.length()) + " character(s) too long!_";
		else if(name.equalsIgnoreCase("main"))
			return "_That name cannot be used!_";

		return null;
	}

	public boolean hasAccount(String name)
	{
		return accountMap.containsKey(name);
	}

	public long getOwner(String name)
	{
		return accountMap.get(name);
	}

	public long getChannel(String name)
	{
		return channelMap.get(name);
	}

	public long createAccount(String name, long ownerID, Consumer<Account> consumer)
	{
		Category category = Objects.requireNonNull(engine.jda.getCategoryById(948059969754906634L));

		TextChannel channel = category.createTextChannel(name).complete();

		long channelID = channel.getIdLong();
		accountMap.put(name, ownerID);
		channelMap.put(name, channelID);
		Account account = new Account(this, ownerID, channelID, name);
		if(consumer != null)
			consumer.accept(account);
		account.save();
		save();

		channel.sendMessage("<@" + ownerID + ">").complete();

		return channelID;
	}

	public Account load(String name)
	{
		try
		{
			return new Account(this, Json.read(accDir.resolve(name + ".json").toFile()));
		}
		catch (FileNotFoundException e)
		{
			throw new UncheckedIOException("Couldn't find file for account: " + name, e);
		}
	}

	void load() throws FileNotFoundException
	{
		channelMap.clear();
		accountMap.clear();

		Path accs = accDir.resolve("main.json");
		if(Files.exists(accs))
		{
			JsonObject obj = Json.read(accs.toFile()).getObject();

			for (Map.Entry<String, JsonValue> entry : obj)
			{
				String name = entry.getKey();
				Path path = accDir.resolve(name + ".json");
				if(Files.exists(path))
				{
					JsonObject obj2 = entry.getValue().getObject();
					long ownerID = obj2.getLong("owner");
					long channelID = obj2.getLong("channel");
					accountMap.put(name, ownerID);
					channelMap.put(name, channelID);
				}
				else
					System.err.println(path + " does not exist! Ignoring '" + name + "' account...");
			}
		}
		else
			System.out.println("No accounts.json!");
	}

	void save()
	{
		try
		{
			Path accs = accDir.resolve("main.json");
			JsonObject obj = new JsonObject();

			JsonObject obj2;
			String name;
			for (Map.Entry<String, Long> entry : accountMap.entrySet())
			{
				name = entry.getKey();
				obj2 = new JsonObject();
				obj2.put("owner", entry.getValue());
				obj2.put("channel", getChannel(name));
				obj.put(name, obj2);
			}

			Json.writer(accs.toFile()).setPrettyPrint().put(obj).close();
		}
		catch (IOException ex)
		{
			throw new UncheckedIOException("Error during saving accounts", ex);
		}
	}
}
