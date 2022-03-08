package com.ch.cryptobot;

import com.hk.io.IOUtil;
import com.hk.json.Json;
import com.hk.json.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public class Util
{
	static Crypto getCrypto(String name)
	{
		try
		{
			return Crypto.valueOf(name.replace(" ", "_").toUpperCase(Locale.ROOT));
		}
		catch(IllegalArgumentException ex)
		{
			for (Crypto cc : Crypto.values())
			{
				if (cc.symbol.equalsIgnoreCase(name))
					return cc;
			}
			return null;
		}
	}

	static String cashFmt(double price)
	{
		String str = NumberFormat.getCurrencyInstance().format(price);
		return str.endsWith(".00") ? str.substring(0, str.length() - 3) : str;
	}

	static String readToken() throws IOException
	{
		InputStream in = Main.class.getResourceAsStream("/token.txt");
		Reader rdr = new InputStreamReader(Objects.requireNonNull(in));
		String s = IOUtil.readString(rdr);
		rdr.close();
		return s;
	}

	static double getCryptoPrice(Crypto crypto)
	{
		try
		{
			URL url = new URL("https://min-api.cryptocompare.com/data/price?fsym=" + crypto.symbol + "&tsyms=USD");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.connect();
			JsonValue value = Json.read(conn.getInputStream(), StandardCharsets.UTF_8);
			double price = value.getObject().getDouble("USD");
			conn.disconnect();
			return price;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	static String joinLast(String[] args, int index)
	{
		StringBuilder sb = new StringBuilder();

		for(int i = index; i < args.length; i++)
			sb.append(args[i]).append(' ');

		sb.setLength(sb.length() - 1);

		return sb.toString();
	}

}
