package com.binance.api.client.store;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerPrice;

public class CachePrice {

	private final BinanceApiRestClient restClient;
	private final String symbol;
	private final String pair;
	private final InfluxDB influxDB;

	public CachePrice(String pair, String symbol) {

		BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
		this.influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086");
		this.restClient = factory.newRestClient();
		this.symbol = symbol;
		this.pair = pair;

	}

	public CachePrice(String pair) {

		BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance();
		this.influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086");
		this.restClient = factory.newRestClient();
		this.symbol = null;
		this.pair = pair;

	}

	private void storePriceForSymbol() {

		BigDecimal oldPrice = BigDecimal.ZERO;
		BigDecimal newPrice = BigDecimal.ZERO;

		BatchPoints batchpoint = BatchPoints.database("stock_order").retentionPolicy("stock_retention").build();

		System.out.println("Cache price for symbol " + symbol + " is running...");
		while (true) {

			TickerPrice tickerPrice = restClient.getPrice(symbol);

			newPrice = new BigDecimal(tickerPrice.getPrice());

			if (!oldPrice.equals(newPrice)) {

				Point point = Point.measurement("pricehistory").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
						.tag("symbol", symbol).addField("price", newPrice).build();

				batchpoint.point(point);
				influxDB.write(batchpoint);

				oldPrice = newPrice;
			}
		}
	}

	private void storePriceForAllSymbol() {

		boolean changeInPrice;
		BigDecimal price;

		Map<String, BigDecimal> symbolPrice = new HashMap<String, BigDecimal>();
		BatchPoints batchpoint = BatchPoints.database("stock_order").retentionPolicy("stock_retention").build();

		System.out.println("Cache price for All " + pair + " symbol is running...");

		while (true) {

			List<TickerPrice> tickerPrice = restClient.getAllPrices();

			for (TickerPrice row : tickerPrice) {

				if (!row.getSymbol().endsWith(pair)) {
					continue;
				}

				changeInPrice = false;
				price = new BigDecimal(row.getPrice());

				if (symbolPrice.containsKey(row.getSymbol())) {

					if (!symbolPrice.get(row.getSymbol()).equals(price)) {
						symbolPrice.put(row.getSymbol(), price);
						changeInPrice = true;
					}
				} else {
					symbolPrice.put(row.getSymbol(), price);
					changeInPrice = true;
				}

				if (changeInPrice) {
					Point point = Point.measurement("pricehistory")
							.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).tag("symbol", row.getSymbol())
							.addField("price", price).build();
					batchpoint.point(point);
				}

			}
			influxDB.write(batchpoint);
		}

	}

	public void cachePrice() {

		if (symbol != null) {
			storePriceForSymbol();
		} else {
			storePriceForAllSymbol();
		}
	}

	public static void main(String[] args) {

//		CachePrice obj = new CachePrice("BTC","ETHBTC");
		CachePrice obj = new CachePrice("BTC");
		obj.cachePrice();

	}

}
