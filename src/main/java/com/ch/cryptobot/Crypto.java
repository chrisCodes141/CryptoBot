package com.ch.cryptobot;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
enum Crypto
{
	BITCOIN("BTC"),
	ETHEREUM("ETH"),
	TERRA("LUNA"),
	BINANCE_COIN("BNB"),
	SOLANA("SOL"),
	XRP(),
	AVALANCHE("AVAX"),
	BUSD(),
	SHIBA_INU("SHIB"),
	CARDANO("ADA"),
	COSMOS("ATOM"),
	TETHER("USDT"),
	POLKADOT("DOT"),
	FANTOM("FTM"),
	WAVES(),
	NEAR(),
	POLYGON("MATIC"),
	UMA(),
	CHAINLINK("LINK"),
	THE_SANDBOX("SAND"),
	LITECOIN("LTC"),
	DOGECOIN("DOGE"),
	DECENTRALAND("MANA"),
	USD_COIN("USDC"),
	AXIE_INFINITY_SHARDS("AXS"),
	TRON("TRX"),
	THETA(),
	TERRAUSD("UST"),
	KYBER_NETWORK("KNC"),
	FILECOIN("FIL"),
	STELLAR("XLM"),
	ETHEREUM_CLASSIC("ETC"),
	ALGORAND("ALGO"),
	BITCOIN_CASH("BCH");

	public final String symbol;

	Crypto()
	{
		this.symbol = name();
	}

	Crypto(String symbol)
	{
		this.symbol = symbol;
	}

	static Crypto bySymbol(String symbol)
	{
		return symbolMap.get(symbol);
	}

	private static final Map<String, Crypto> symbolMap = new HashMap<>();

	static
	{
		for (Crypto crypto : values())
			symbolMap.put(crypto.symbol, crypto);
	}
}
