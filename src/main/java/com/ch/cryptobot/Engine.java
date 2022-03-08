package com.ch.cryptobot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Engine
{
	final JDA jda;
	final Path accDir;
	final Accounts accounts;
	final Map<String, Command> commandMap;
	final ScheduledExecutorService service;

	public Engine(JDA jda, Path path) throws FileNotFoundException
	{
		this.jda = jda;
		accDir = path.toAbsolutePath();

		commandMap = new HashMap<>();
		commandMap.put("price", this::priceCommand);
		commandMap.put("coins", this::coinsCommand);
		commandMap.put("account", this::accountCommand);
		commandMap.put("accounts", this::accountsCommand);
		commandMap.put("buy", (msg, args, account) -> buySellCommand(msg, args, account, true));
		commandMap.put("sell", (msg, args, account) -> buySellCommand(msg, args, account, false));
		commandMap.put("in", this::inCommand);
		commandMap.put("every", this::everyCommand);
		commandMap.put("trades", this::tradesCommand);
		commandMap.put("balance", this::balanceCommand);
		commandMap.put("loan", this::loanCommand);
		commandMap.put("loans", this::loansCommand);
		commandMap.put("clone", this::cloneCommand);
		commandMap.put("save", this::saveCommand);
		commandMap.put("help", this::helpCommand);

		if(!Files.exists(accDir))
			throw new FileNotFoundException("Accounts directory doesn't exist: " + accDir);

		service = Executors.newSingleThreadScheduledExecutor();

		accounts = new Accounts(this, accDir);

		Runtime.getRuntime().addShutdownHook(new Thread(this::save));
	}

	void run(Message msg, String cmd, String[] args, String channel)
	{
		System.out.println("CALLED: '" + cmd + "' " + Arrays.toString(args) + " in " + channel);
		channel = channel.equalsIgnoreCase("main") ? null : channel;

		try
		{
			Command command = commandMap.get(cmd.toLowerCase(Locale.ROOT));

			if (command == null)
				msg.reply("_No such command..._ `" + cmd + "`\ntry `!help`").complete();
			else
				command.run(msg, args, channel);
		}
		catch (Exception e)
		{
			System.err.println("Issue with command: " + cmd);
			e.printStackTrace();
		}
	}

	private void priceCommand(Message msg, String[] args, String accountName)
	{
		if (args.length == 0)
		{
			msg.reply("_Use it like_ `!price <crypto>` _ex._ `!price BTC` _or_ `!price dogecoin`").complete();
			return;
		}
		String coin = Util.joinLast(args, 0);
		Crypto crypto = Util.getCrypto(coin);

		if (crypto == null)
		{
			msg.reply("_No such crypto..._ `" + coin + "`. _It might not be registered in the bot._").complete();
			return;
		}

		msg.reply("**" + crypto.symbol + "**: `" + Util.cashFmt(Util.getCryptoPrice(crypto)) + "`").complete();
	}

	private void coinsCommand(Message msg, String[] args, String accountName)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("_Current Coins:_\n```\n");

		String name;
		for (Crypto crypto : Crypto.values())
		{
			name = crypto.toString().toLowerCase(Locale.ROOT).replace("_", " ");
			sb.append(name).append(": ").append(crypto.symbol).append('\n');
		}
		sb.setLength(sb.length() - 1);

		sb.append("```");
		msg.reply(sb).complete();
	}

	private void accountsCommand(Message msg, String[] args, String accountName)
	{
		long id = msg.getAuthor().getIdLong();

		StringBuilder sb = new StringBuilder();
		int amt = 0;
		for (String account : accounts.getAccounts())
		{
			if(accounts.getOwner(account) == id)
			{
				sb.append("<#").append(accounts.getChannel(account)).append(">\n");
				amt++;
			}
		}
		if(sb.length() > 0)
			sb.setLength(sb.length() - 1);

		if(amt == 0)
			msg.reply("_You don't have any accounts! Try:_ `!account <name>`").complete();
		else
			msg.reply(sb).complete();
	}

	private void accountCommand(Message msg, String[] args, String accountName)
	{
		if (args.length == 0)
		{
			msg.reply("_Use it like_ `!account <name>` _ex._ `!account Chris` _or_ `!account Tig-Ol-Bitties`").complete();
			return;
		}
		if (accountName != null)
		{
			msg.reply(mainUsage()).complete();
			return;
		}

		String name = Util.joinLast(args, 0);
		long authorID = msg.getAuthor().getIdLong();

		if (accounts.hasAccount(name))
		{
			long id = accounts.getOwner(name);
			if(id == authorID)
			{
				long accChannelID = accounts.getChannel(name);
				TextChannel ch = jda.getTextChannelById(accChannelID);
				Objects.requireNonNull(ch).sendMessage("<@" + id + ">").complete();
			}
			else
				msg.reply("_It seems this account is already owned by_ <@" + id + ">_!_").complete();

			return;
		}
		String invalidMessage = accounts.getInvalidMessage(name);
		if (invalidMessage != null)
		{
			msg.reply(invalidMessage).complete();
			return;
		}

		long channelID = accounts.createAccount(name, authorID, null);
		msg.reply("**ACCOUNT AND CHANNEL CREATED!** <#" + channelID + ">").complete();
	}

	private void buySellCommand(Message msg, String[] args, String accountName, boolean buy)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}
		if(accounts.getOwner(accountName) != msg.getAuthor().getIdLong())
		{
			msg.reply(notYourAccount()).complete();
			return;
		}

		String str = buy ? "buy" : "sell";
		if(args.length != 2)
		{
			msg.reply("_Use it like_ `!" + str + " <amount> <crypto>` _ex._ `!" + str + " 10000 DOGE` _or_ `!" + str + " 0.27297441 ethereum`").complete();
			return;
		}

		BigDecimal amount;
		try
		{
			amount = new BigDecimal(args[0]);

			if(amount.signum() <= 0)
				throw new NumberFormatException();
		}
		catch (NumberFormatException ex)
		{
			msg.reply(decimalNumber()).complete();
			return;
		}

		String coin = Util.joinLast(args, 1);
		Crypto crypto = Util.getCrypto(coin);
		if(crypto == null)
		{
			msg.reply("_No such crypto..._ `" + coin + "`. _It might not be registered in the bot._").complete();
			return;
		}

		boolean dirty;
		Account account = accounts.load(accountName);
		BigDecimal unitPrice = BigDecimal.valueOf(Util.getCryptoPrice(crypto));
		BigDecimal price = unitPrice.multiply(amount).setScale(2, buy ? RoundingMode.UP : RoundingMode.DOWN);
		BigDecimal money = account.getMoney();
		BigDecimal balance = account.getBalance(crypto);
		BigDecimal newMoney = null, newBalance = null;

		StringBuilder sb = new StringBuilder();
		if(buy)
		{
			dirty = money.subtract(price).signum() >= 0;

			if(dirty)
			{
				newMoney = money.subtract(price);
				newBalance = balance.add(amount);

				account.setMoney(newMoney);
				account.setBalance(crypto, newBalance);
			}
			else
			{
				sb.append("_You can't afford this! You need_ `");
				sb.append(Util.cashFmt(price.doubleValue()));
				sb.append("` _and you have_ `");
				sb.append(Util.cashFmt(money.doubleValue()));
				sb.append("`");
			}
		}
		else
		{
			dirty = balance.subtract(amount).signum() >= 0;

			if(dirty)
			{
				newMoney = money.add(price);
				newBalance = balance.subtract(amount);

				account.setMoney(newMoney);
				account.setBalance(crypto, newBalance);
			}
			else
			{
				sb.append("_You can't afford this! You need_ `");
				sb.append(amount.toPlainString());
				sb.append("` _");
				sb.append(crypto.symbol);
				sb.append(" and you have_ `");
				sb.append(balance.toPlainString());
				sb.append("`");
			}
		}

		if(dirty)
		{
			if(buy)
				sb.append("_Purchasing_ `");
			else
				sb.append("_Selling_ `");

			sb.append(amount.toPlainString());
			sb.append("` _");
			sb.append(crypto.symbol);
			sb.append(" for_ `");
			sb.append(Util.cashFmt(price.doubleValue()));
			sb.append("`\n");

			sb.append("**Money:** `");
			sb.append(Util.cashFmt(money.doubleValue()));
			sb.append("` -> `");
			sb.append(Util.cashFmt(newMoney.doubleValue()));
			sb.append("`");

			account.addTransaction(new Transaction(money, newMoney, price, balance, newBalance, amount, unitPrice, crypto, buy, false));

			account.save();
		}
		msg.reply(sb).complete();
	}

	private void inCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}
		if(accounts.getOwner(accountName) != msg.getAuthor().getIdLong())
		{
			msg.reply(notYourAccount()).complete();
			return;
		}

		if(args.length != 4)
		{
			msg.reply("_Use it like_ `!in <time> <buy/sell> <amount> <crypto>` _ex._ `!in 20m sell 10000 DOGE` _or_ `!in 3h buy 0.27297441 ethereum`").complete();
			return;
		}

		long minutes;
		try
		{
			minutes = getMinutes(args[0]);
		}
		catch (NumberFormatException ex)
		{
			msg.reply("_Expected a time to perform transaction, see 'time' in_ `!help`").complete();
			return;
		}

		boolean buy;
		if(args[1].equalsIgnoreCase("buy"))
			buy = true;
		else if(args[1].equalsIgnoreCase("sell"))
			buy = false;
		else
		{
			msg.reply("_Expected_ `buy` _or_ `sell` _after_ `in`").complete();
			return;
		}


		BigDecimal amount;
		try
		{
			amount = new BigDecimal(args[2]);

			if(amount.signum() <= 0)
				throw new NumberFormatException();
		}
		catch (NumberFormatException ex)
		{
			msg.reply(decimalNumber()).complete();
			return;
		}

		String coin = Util.joinLast(args, 3);
		Crypto crypto = Util.getCrypto(coin);
		if(crypto == null)
		{
			msg.reply("_No such crypto..._ `" + coin + "`. _It might not be registered in the bot._").complete();
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("_Scheduled to_ **");
		sb.append(buy ? "BUY" : "SELL");
		sb.append(" `");
		sb.append(amount.toPlainString());
		sb.append(" ");
		sb.append(crypto.symbol);
		sb.append("`** _in_ `");
		sb.append(args[0]);
		sb.append("`");
		long msgID = msg.getIdLong();
		msg.reply(sb).complete();

		service.schedule(new ScheduledTransaction(accountName, crypto, amount, buy, msgID), minutes, TimeUnit.MINUTES);
	}

	private void everyCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}
		if(accounts.getOwner(accountName) != msg.getAuthor().getIdLong())
		{
			msg.reply(notYourAccount()).complete();
			return;
		}

		if(args.length != 4)
		{
			msg.reply("_Use it like_ `!every <time> <buy/sell> <amount> <crypto>` _ex._ `!every 20m sell 10000 DOGE` _or_ `!every 3h buy 0.27297441 ethereum`").complete();
			return;
		}

		long minutes;
		try
		{
			minutes = getMinutes(args[0]);
		}
		catch (NumberFormatException ex)
		{
			msg.reply("_Expected a time to perform transaction, see 'time' in_ `!help`").complete();
			return;
		}

		boolean buy;
		if(args[1].equalsIgnoreCase("buy"))
			buy = true;
		else if(args[1].equalsIgnoreCase("sell"))
			buy = false;
		else
		{
			msg.reply("_Expected_ `buy` _or_ `sell` _after_ `in`").complete();
			return;
		}


		BigDecimal amount;
		try
		{
			amount = new BigDecimal(args[2]);

			if(amount.signum() <= 0)
				throw new NumberFormatException();
		}
		catch (NumberFormatException ex)
		{
			msg.reply(decimalNumber()).complete();
			return;
		}

		String coin = Util.joinLast(args, 3);
		Crypto crypto = Util.getCrypto(coin);
		if(crypto == null)
		{
			msg.reply("_No such crypto..._ `" + coin + "`. _It might not be registered in the bot._").complete();
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("_Scheduled to_ **");
		sb.append(buy ? "BUY" : "SELL");
		sb.append(" `");
		sb.append(amount.toPlainString());
		sb.append(" ");
		sb.append(crypto.symbol);
		sb.append("`** _every_ `");
		sb.append(args[0]);
		sb.append("`");
		long msgID = msg.getIdLong();
		msg.reply(sb).complete();

		service.scheduleAtFixedRate(new ScheduledTransaction(accountName, crypto, amount, buy, msgID), minutes, minutes, TimeUnit.MINUTES);
	}

	private void tradesCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}

		Account account = accounts.load(accountName);

		StringBuilder sb = new StringBuilder();
		sb.append("**All Transactions:**\n");
		for (Transaction transaction : account.getTransactions())
		{
			sb.append('`');
			sb.append(dateFormat.format(transaction.timestamp));
			sb.append("` ");

			if(transaction.buy)
				sb.append("Bought ");
			else
				sb.append("Sold ");

			sb.append('`');
			sb.append(transaction.amount.toPlainString());
			sb.append("` _");
			sb.append(transaction.crypto.symbol);
			sb.append(" for_ `");
			sb.append(Util.cashFmt(transaction.unitPrice.doubleValue()));
			sb.append("` _ea. total_ `");
			sb.append(Util.cashFmt(transaction.price.doubleValue()));
			sb.append("` Money: `");
			sb.append(Util.cashFmt(transaction.prevMoney.doubleValue()));
			sb.append("` to `");
			sb.append(Util.cashFmt(transaction.newMoney.doubleValue()));
			sb.append("`");

			if(transaction.auto)
				sb.append(" `AUTO`");

			sb.append("\n");
		}
		sb.setLength(sb.length() - 1);

		msg.reply(sb).complete();
	}

	private void balanceCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}

		Account account = accounts.load(accountName);
		StringBuilder sb = new StringBuilder();

		sb.append("**Account Money:** `").append(Util.cashFmt(account.getMoney().doubleValue())).append("`");

		if(account.hasBalances())
		{
			BigDecimal total = account.getMoney(), totalCrypto = BigDecimal.ZERO;
			sb.append("\n**Crypto Balances:**\n");

			BigDecimal bal;
			for (Crypto crypto : Crypto.values())
			{
				bal = account.getBalance(crypto);
				if(bal.signum() != 0)
				{
					BigDecimal cashAmt = BigDecimal.valueOf(Util.getCryptoPrice(crypto));
					String first = Util.cashFmt(cashAmt.doubleValue());
					cashAmt = cashAmt.multiply(bal);
					sb.append("_");
					sb.append(crypto.symbol);
					sb.append("_: `");
					sb.append(bal.toPlainString());
					sb.append(" / 1` _USD:_ `");
					sb.append(Util.cashFmt(cashAmt.doubleValue()));
					sb.append(" / ");
					sb.append(first);
					sb.append("`\n");
					total = total.add(cashAmt);
					totalCrypto = totalCrypto.add(cashAmt);
				}
			}

			if(total.equals(totalCrypto))
				sb.append("**Total Cash Amount:** `").append(Util.cashFmt(total.doubleValue())).append("`");
			else
				sb.append("**Total Cash Amount:** `").append(Util.cashFmt(total.doubleValue())).append("` _without money:_ `").append(Util.cashFmt(totalCrypto.doubleValue())).append("`");
		}

		msg.reply(sb).complete();
	}

	private void loanCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}
		if(accounts.getOwner(accountName) != msg.getAuthor().getIdLong())
		{
			msg.reply(notYourAccount()).complete();
			return;
		}
		if(args.length != 1)
		{
			msg.reply("_Use it like_ `!loan <amount>` _ex._ `!loan 100000` _or_ `!loan 0.01`").complete();
			return;
		}

		BigDecimal amount;
		try
		{
			amount = new BigDecimal(args[0]);
			amount = amount.setScale(2, RoundingMode.HALF_UP);

			if(amount.signum() <= 0)
				throw new NumberFormatException();
		}
		catch (NumberFormatException ex)
		{
			msg.reply(decimalNumber()).complete();
			return;
		}

		Date timestamp = new Date();
		Account account = accounts.load(accountName);
		BigDecimal money = account.getMoney();
		BigDecimal newMoney = money.add(amount);
		account.setMoney(newMoney);
		account.addLoan(timestamp, amount);
		account.save();

		msg.reply("_Loan approved for_ `" + Util.cashFmt(amount.doubleValue()) + "`_! Balance from_ `" + Util.cashFmt(money.doubleValue()) + "` _to_ `" + Util.cashFmt(newMoney.doubleValue()) + "`").complete();
	}

	private void loansCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}

		Account account = accounts.load(accountName);
		Set<Map.Entry<Date, BigDecimal>> loans = account.getLoans();

		if(loans.isEmpty())
		{
			msg.reply("_No loans taken out by this account! Try_ `!loan 20` _to borrow $20, then try this_").complete();
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**Loans:**\n```\n");
		for (Map.Entry<Date, BigDecimal> loan : loans)
		{
			sb.append(dateFormat.format(loan.getKey()));
			sb.append(" -> ");
			sb.append(Util.cashFmt(loan.getValue().doubleValue()));
			sb.append('\n');
		}
		sb.append("```");

		msg.reply(sb).complete();
	}

	private void cloneCommand(Message msg, String[] args, String accountName)
	{
		if(accountName == null)
		{
			msg.reply(notMainUsage()).complete();
			return;
		}
		if(args.length != 1)
		{
			msg.reply("_Use it like_ `!clone <name>` _ex._ `!clone Big-Ol-Titties` _or_ `!clone " + accountName + "1`").complete();
			return;
		}

		msg.reply("_CLONE THIS ACCOUNT_").complete();
	}

	private void saveCommand(Message msg, String[] args, String accountName)
	{
		Message message = msg.reply("_Saving..._").complete();
		save();
		message.editMessage("**Saved!**").complete();
	}

	private void helpCommand(Message msg, String[] args, String accountName)
	{
		if(accountName != null && accounts.getOwner(accountName) != msg.getAuthor().getIdLong())
		{
			msg.reply("**This isn't your account!**").complete();
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**Commands:**\n```\n");
		sb.append("!price <crypto> - Get current price of some crypto currency.\n");
		sb.append("!coins - Get all registered cryptos with this bot.\n");
		sb.append("!accounts - List all of your accounts\n");

		if (accountName != null)
		{
			sb.append("!buy <amount> <crypto> - Buy an amount of crypto using an amount of account money.\n");
			sb.append("!sell <amount> <crypto> - Sell an amount of crypto using a balance of account crypto.\n");
			sb.append("!in <time> <buy/sell> <amount> <crypto> - Buy/Sell an amount of crypto after a certain amount of time (see time)\n");
			sb.append("!every <time> <buy/sell> <amount> <crypto> - Buy/Sell an amount of crypto every period of time (see time)\n");
			sb.append("!balance - Show your current cash and crypto balances.\n");
			sb.append("!loan <amount> - Loan a certain amount of money from the 'bank'.\n");
			sb.append("!loans - List all loans taken by this account.\n");
			sb.append("!trades - List all buy/sell trades performed by this account.\n");
		}
		else
			sb.append("!account <name> - Create a new fake crypto account with a given name\n");

		sb.append("!save - Save all accounts\n");
		sb.append("!help - Print this message\n");

		if (accountName != null)
		{
			sb.append("\n");
			sb.append("time:\n");
			sb.append("Time can be specified as an amount of minutes, hours, or days.\n");
			sb.append("\tSome Examples:\n");
			sb.append("\t\t'10' = 10 minutes, '2h' = 2 hours, '12h' = 12 hours\n");
			sb.append("\t\t'30m' = 30 minutes, '5d' = 5 days, '1hour' = one hour\n");
			sb.append("Minutes can be specified using m, ms, min, or mins\n");
			sb.append("Hours can be specified using h, hs, hr, hrs, hour, or hours\n");
			sb.append("Days can be specified using d, ds, day, days\n");
		}
		sb.append("```");
		msg.reply(sb).complete();
	}

	private long getMinutes(String arg)
	{
		long minutes;
		String s5 = arg.length() >= 5 ? arg.substring(0, arg.length() - 5) : null;
		String s4 = arg.length() >= 4 ? arg.substring(0, arg.length() - 4) : null;
		String s3 = arg.length() >= 3 ? arg.substring(0, arg.length() - 3) : null;
		String s2 = arg.length() >= 2 ? arg.substring(0, arg.length() - 2) : null;
		String s1 = arg.substring(0, arg.length() - 1);

		if (arg.endsWith("mins"))
			minutes = Long.parseLong(s4);
		else if (arg.endsWith("min"))
			minutes = Long.parseLong(s3);
		else if (arg.endsWith("ms"))
			minutes = Long.parseLong(s2);
		else if (arg.endsWith("m"))
			minutes = Long.parseLong(s1);
		else if (arg.endsWith("hours"))
			minutes = Long.parseLong(s5) * 60;
		else if (arg.endsWith("hour"))
			minutes = Long.parseLong(s4) * 60;
		else if (arg.endsWith("hrs"))
			minutes = Long.parseLong(s3) * 60;
		else if (arg.endsWith("hr"))
			minutes = Long.parseLong(s2) * 60;
		else if (arg.endsWith("hs"))
			minutes = Long.parseLong(s2) * 60;
		else if (arg.endsWith("h"))
			minutes = Long.parseLong(s1) * 60;
		else if (arg.endsWith("days"))
			minutes = Long.parseLong(s4) * 60 * 24;
		else if (arg.endsWith("day"))
			minutes = Long.parseLong(s3) * 60 * 24;
		else if (arg.endsWith("ds"))
			minutes = Long.parseLong(s2) * 60 * 24;
		else if (arg.endsWith("d"))
			minutes = Long.parseLong(s1) * 60 * 24;
		else
			minutes = Long.parseLong(arg);

		return minutes;
	}

	void save()
	{
		System.out.println("Saving!");
		service.shutdown();
		accounts.save();
	}

	private String mainUsage()
	{
		return "_Use this in the_ <#948060159022891078> _channel._";
	}

	private String notMainUsage()
	{
		return "_You can't use this in the_ <#948060159022891078> _channel!_";
	}

	private String notYourAccount()
	{
		return "**This isn't your account!**";
	}

	private String decimalNumber()
	{
		return "_Expected amount to be a positive decimal number! ex._ `1`, `0.5`, `414.4859`, `0.27297441`";
	}

	interface Command
	{
		void run(Message msg, String[] args, String account) throws Exception;
	}

	static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm aa");

	private class ScheduledTransaction implements Runnable
	{
		private final String accountName;
		private final Crypto crypto;
		private final BigDecimal amount;
		private final boolean buy;
		private final long msgID;

		public ScheduledTransaction(String accountName, Crypto crypto, BigDecimal amount, boolean buy, long msgID)
		{
			this.accountName = accountName;
			this.crypto = crypto;
			this.amount = amount;
			this.buy = buy;
			this.msgID = msgID;
		}

		@Override
		public void run()
		{
			Account account = accounts.load(accountName);
			BigDecimal unitPrice = BigDecimal.valueOf(Util.getCryptoPrice(crypto));
			BigDecimal price = unitPrice.multiply(amount).setScale(2, buy ? RoundingMode.UP : RoundingMode.DOWN);
			BigDecimal money = account.getMoney();
			BigDecimal balance = account.getBalance(crypto);
			BigDecimal newMoney = null, newBalance = null;
			StringBuilder sb2 = new StringBuilder();
			boolean dirty = false;

			if (buy)
			{
				if (money.subtract(price).signum() >= 0)
				{
					newMoney = money.subtract(price);
					newBalance = balance.add(amount);

					account.setMoney(newMoney);
					account.setBalance(crypto, newBalance);
					dirty = true;
				}
				else
				{
					sb2.append("_You can't afford this! You need_ `");
					sb2.append(Util.cashFmt(price.doubleValue()));
					sb2.append("` _and you have_ `");
					sb2.append(Util.cashFmt(money.doubleValue()));
					sb2.append("`");
				}
			}
			else
			{
				if (balance.subtract(amount).signum() >= 0)
				{
					newMoney = money.add(price);
					newBalance = balance.subtract(amount);

					account.setMoney(newMoney);
					account.setBalance(crypto, newBalance);
					dirty = true;
				}
				else
				{
					sb2.append("_You can't afford this! You need_ `");
					sb2.append(amount.toPlainString());
					sb2.append("` _");
					sb2.append(crypto.symbol);
					sb2.append(" and you have_ `");
					sb2.append(balance.toPlainString());
					sb2.append("`");
				}
			}

			if (dirty)
			{
				if (buy)
					sb2.append("_Purchasing_ `");
				else
					sb2.append("_Selling_ `");

				sb2.append(amount.toPlainString());
				sb2.append("` _");
				sb2.append(crypto.symbol);
				sb2.append(" for_ `");
				sb2.append(Util.cashFmt(price.doubleValue()));
				sb2.append("`\n");

				sb2.append("**Money:** `");
				sb2.append(Util.cashFmt(money.doubleValue()));
				sb2.append("` -> `");
				sb2.append(Util.cashFmt(newMoney.doubleValue()));
				sb2.append("`");

				account.addTransaction(new Transaction(money, newMoney, price, balance, newBalance, amount, unitPrice, crypto, buy, true));
				account.save();
			}

			Objects.requireNonNull(jda.getTextChannelById(accounts.getChannel(accountName)))
					.sendMessage(sb2).referenceById(msgID).complete();
		}
	}
}
