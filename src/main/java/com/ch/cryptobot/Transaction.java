package com.ch.cryptobot;

import com.hk.json.JsonObject;

import java.math.BigDecimal;
import java.util.Date;

public class Transaction
{
	final Date timestamp;
	final BigDecimal prevMoney, newMoney, price;
	final BigDecimal prevBalance, newBalance, amount, unitPrice;
	final Crypto crypto;
	final boolean buy, auto;

	public Transaction(JsonObject obj)
	{
		timestamp = new Date(obj.getLong("ts"));
		prevMoney = new BigDecimal(obj.getString("pm"));
		newMoney = new BigDecimal(obj.getString("nm"));
		price = new BigDecimal(obj.getString("p"));
		prevBalance = new BigDecimal(obj.getString("pb"));
		newBalance = new BigDecimal(obj.getString("nb"));
		amount = new BigDecimal(obj.getString("amt"));
		unitPrice = new BigDecimal(obj.getString("up"));

		crypto = Crypto.bySymbol(obj.getString("c"));
		buy = obj.getBoolean("b");
		auto = obj.getBoolean("a");
	}

	public Transaction(BigDecimal prevMoney, BigDecimal newMoney, BigDecimal price, BigDecimal prevBalance, BigDecimal newBalance, BigDecimal amount, BigDecimal unitPrice, Crypto crypto, boolean buy, boolean auto)
	{
		this.timestamp = new Date();
		this.prevMoney = prevMoney;
		this.newMoney = newMoney;
		this.price = price;
		this.prevBalance = prevBalance;
		this.newBalance = newBalance;
		this.amount = amount;
		this.unitPrice = unitPrice;
		this.crypto = crypto;
		this.buy = buy;
		this.auto = auto;
	}

	JsonObject toJson()
	{
		JsonObject obj = new JsonObject();

		obj.put("ts", timestamp.getTime());
		obj.put("pm", prevMoney.toPlainString());
		obj.put("nm", newMoney.toPlainString());
		obj.put("p", price.toPlainString());
		obj.put("pb", prevBalance.toPlainString());
		obj.put("nb", newBalance.toPlainString());
		obj.put("amt", amount.toPlainString());
		obj.put("up", unitPrice.toPlainString());

		obj.put("c", crypto.symbol);
		obj.put("b", buy);
		obj.put("a", auto);

		return obj;
	}
}
