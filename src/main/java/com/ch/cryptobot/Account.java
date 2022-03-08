package com.ch.cryptobot;

import com.hk.json.*;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.*;

class Account
{
	final Accounts accounts;
	final long ownerID;
	final long channelID;
	final String name;
	private BigDecimal money;
	private final Map<Crypto, BigDecimal> balances;
	private final Map<Date, BigDecimal> loans;
	private final List<Transaction> transactions;

	Account(Accounts accounts, long ownerID, long channelID, String name)
	{
		this.accounts = accounts;
		this.ownerID = ownerID;
		this.channelID = channelID;
		this.name = name;

		money = BigDecimal.ZERO;
		balances = new EnumMap<>(Crypto.class);
		loans = new LinkedHashMap<>();
		transactions = new LinkedList<>();
	}

	Account(Accounts accounts, JsonValue value) throws JsonAdaptationException
	{
		this.accounts = accounts;

		if(!value.isObject())
			throw new JsonAdaptationException("expected json object");
		JsonObject obj = value.getObject();

		this.ownerID = obj.getLong("owner");
		this.channelID = obj.getLong("channel");
		this.name = obj.getString("name");

		if(obj.contains("money"))
			setMoney(new BigDecimal(obj.getString("money")));

		balances = new EnumMap<>(Crypto.class);
		if(obj.contains("balances"))
		{
			JsonObject balances = obj.getObject("balances");

			Crypto crypto;
			BigDecimal balance;
			for (Map.Entry<String, JsonValue> entry : balances)
			{
				crypto = Crypto.bySymbol(entry.getKey());
				balance = new BigDecimal(entry.getValue().getString());
				setBalance(crypto, balance);
			}
		}

		loans = new LinkedHashMap<>();
		if(obj.contains("loans"))
		{
			JsonObject loans = obj.getObject("loans");

			Date timestamp;
			BigDecimal amount;
			for (Map.Entry<String, JsonValue> entry : loans)
			{
				timestamp = new Date(Long.parseLong(entry.getKey()));
				amount = new BigDecimal(entry.getValue().getString());
				this.loans.put(timestamp, amount);
			}
		}

		transactions = new LinkedList<>();
		if(obj.contains("transactions"))
		{
			JsonArray transactions = obj.getArray("transactions");
			for (JsonValue transaction : transactions)
				this.transactions.add(new Transaction(transaction.getObject()));
		}
	}

	BigDecimal getMoney()
	{
		return money;
	}

	void setMoney(BigDecimal money)
	{
		this.money = money.setScale(2, RoundingMode.FLOOR);
	}

	boolean hasBalances()
	{
		return !balances.isEmpty();
	}

	BigDecimal getBalance(Crypto crypto)
	{
		return balances.getOrDefault(crypto, BigDecimal.ZERO);
	}

	void setBalance(Crypto crypto, BigDecimal balance)
	{
		if(balance.signum() == 0)
			balances.remove(crypto);
		else
			balances.put(crypto, balance.stripTrailingZeros());
	}

	void addLoan(Date timestamp, BigDecimal amount)
	{
		loans.put(timestamp, amount);
	}

	Set<Map.Entry<Date, BigDecimal>> getLoans()
	{
		return loans.entrySet();
	}

	public void addTransaction(Transaction transaction)
	{
		transactions.add(transaction);
	}

	List<Transaction> getTransactions()
	{
		return transactions;
	}

	void save()
	{
		Path path = accounts.accDir.resolve(name + ".json");
		try
		{
			JsonObject obj = new JsonObject();

			obj.put("owner", ownerID);
			obj.put("channel", channelID);
			obj.put("name", name);

			obj.put("money", money.toPlainString());

			if(!balances.isEmpty())
			{
				JsonObject balances = new JsonObject();
				for (Map.Entry<Crypto, BigDecimal> entry : this.balances.entrySet())
					balances.put(entry.getKey().symbol, entry.getValue().toPlainString());

				obj.put("balances", balances);
			}
			if(!loans.isEmpty())
			{
				JsonObject loans = new JsonObject();
				for (Map.Entry<Date, BigDecimal> entry : this.loans.entrySet())
					loans.put(Long.toString(entry.getKey().getTime()), entry.getValue().toPlainString());

				obj.put("loans", loans);
			}
			if(!transactions.isEmpty())
			{
				JsonArray transactions = new JsonArray();
				for (Transaction transaction : this.transactions)
					transactions.add(transaction.toJson());

				obj.put("transactions", transactions);
			}
			Json.writer(path.toFile()).setPrettyPrint().put(obj).close();
		}
		catch (FileNotFoundException e)
		{
			throw new UncheckedIOException("Issue saving account to file: " + name, e);
		}
	}
}
