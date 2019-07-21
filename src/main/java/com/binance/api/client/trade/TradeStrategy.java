package com.binance.api.client.trade;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.constant.BinanceApiConstants;
import com.binance.api.client.trade.BuySellStream;

public class TradeStrategy {

	private static final float expectedLast24ChangePrecentBTCUSDT = (float) 3.0;
	private static final String deltaChangeInLastTime = "10m";
	private static final float expectedDeltaChangePercent = (float) 1.5;

	private final String symbol;
	private final BinanceApiRestClient restClient;
	private final InfluxDB influxDB;
	private final BuySellStream objBuySellStream;
	private final OrderStrategy orderStrategy;

	private BigDecimal highestBid;
	private BigDecimal lowestAsk;

	public TradeStrategy(String symbol, String runMode, String apiKey, String secretKey) {

		BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(apiKey, secretKey);
		this.restClient = factory.newRestClient();
		this.symbol = symbol;
		this.influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086");
		this.objBuySellStream = new BuySellStream(symbol);
		this.orderStrategy = new OrderStrategy(symbol, runMode, this.restClient);
	}

	private boolean check24HrsChangeBTCUSDT() {

		Float changePercent;

		System.out.println("********** CONDITION 1 ****************");

		TickerStatistics tickerStatistics = restClient.get24HrPriceStatistics("BTCUSDT");
		changePercent = Float.parseFloat(tickerStatistics.getPriceChangePercent());

		boolean status = changePercent < expectedLast24ChangePrecentBTCUSDT ? true : false;
		System.out.println("BTCUSDT 24change Percentage " + changePercent + " < " + expectedLast24ChangePrecentBTCUSDT
				+ " :: " + status);

		return status;
	}

	private List<List<Object>> selectQueryDB(String queryString) {

		List<List<Object>> records = null;

//		System.out.println(queryString);

		Query query = new Query(queryString, "stock_order");

		List<QueryResult.Result> resutls = influxDB.query(query).getResults();

		if (resutls.get(0).getSeries() != null) {
			records = resutls.get(0).getSeries().get(0).getValues();
		}
		return records;
	}

	private boolean checkLastXMinsDeltaChange() {

		String queryString;

		System.out.println("********** CONDITION 2 ****************");

		queryString = String.format(" select ((last(price)-first(price))/first(price))*100 as deltaChangePercent "
				+ "from pricehistory where symbol = '%s' and time >= now()-%s", symbol, deltaChangeInLastTime);

		List<List<Object>> records = selectQueryDB(queryString);

		if (records != null) {

			for (List<Object> row : records) {

				double deltaChangePercent = (double) row.get(1);

				boolean status = deltaChangePercent < expectedDeltaChangePercent ? true : false;

				System.out.println(symbol + " delta change in last " + deltaChangeInLastTime + " " + deltaChangePercent
						+ " < " + expectedDeltaChangePercent + " :: " + status);
				return status;
			}
		}

		System.out.println(symbol + " delta change in last " + deltaChangeInLastTime + " : No change :: true");
		return true;
	}

	private boolean checkContinuosBuySell() {

		Map<String, BigDecimal> buysellDetails = objBuySellStream.getBuySellDetails();
		BigDecimal BTC3 = new BigDecimal(3);
		BigDecimal BTC4 = new BigDecimal(4);

		if (buysellDetails.get("btcBidVolume").compareTo(BTC3) >= 0) {
			if (buysellDetails.get("btcBidVolume").compareTo(BTC4) >= 0
					|| buysellDetails.get("btcBidVolume").compareTo(buysellDetails.get("btcAskVolume")) == 1) {

				highestBid = buysellDetails.get("highestBid");
				lowestAsk = buysellDetails.get("lowestAsk");
				System.out.println("Check BTC volume condition " + buysellDetails + " :: true");
				return true;

			} else {
				System.out.println("Check BTC volume condition " + buysellDetails + " :: false");
				return false;
			}
		}
		System.out.println("Check BTC volume condition " + buysellDetails + " :: false");
		return false;
	}

	private boolean checkContinuos10SecWindow() {

		SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS");
		long endTime = System.currentTimeMillis() + 10000;

		System.out.println("********** CONDITION 3 & 4 ************");
		while (System.currentTimeMillis() <= endTime) {
			
			System.out.print(formatter.format(new Date(System.currentTimeMillis())) + " :: ");
			
			if (checkContinuosBuySell() == false) {
				System.out.println("Checked BTC volume conditions for continuos 10sec window :: false");
				return false;
			}
		}
		System.out.println("Checked BTC volume conditions for continuos 10sec window :: true");
		return true;
	}

	public void executeStrategy() {

		System.out.println("Order Strategy for the Pair :: " + symbol);
		if ((check24HrsChangeBTCUSDT() == true) && (checkLastXMinsDeltaChange() == true)
				&& (checkContinuos10SecWindow() == true)) {

			orderStrategy.placeOrders(highestBid, lowestAsk);

			System.out.println("\n********************************************");
		} else {
			System.out.println("**********************************************");
			System.out.println("Condition not satified :: Order could not be placed ");
		}

	}

	public static void main(String[] args) {

		Options options = new Options();
		Option option;

		option = new Option("a", "altcoin", true, "BTC pair alt-coin eg: ETHBTC");
		option.setRequired(true);
		options.addOption(option);

		option = new Option("m", "runmode", true, "Run mode either test/real");
		option.setRequired(true);
		options.addOption(option);

		option = new Option("k", "apikey", true, "Binance API-Key");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("s", "secretkey", true, "Binance Secret-Key");
		option.setRequired(false);
		options.addOption(option);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;
		String altcoin = null;
		String runmode = null;
		String apikey = null;
		String secretkey = null;

		try {

			cmd = parser.parse(options, args);

			altcoin = cmd.getOptionValue("altcoin");
			runmode = cmd.getOptionValue("runmode");

			if (!(runmode.equalsIgnoreCase("test") || runmode.equalsIgnoreCase("real"))) {
				throw new ParseException("Invalid argument: m (Must be either test or real)");
			}

			apikey = cmd.getOptionValue("apikey", BinanceApiConstants.ENDPOINT_SECURITY_TYPE_APIKEY);
			secretkey = cmd.getOptionValue("secretkey", BinanceApiConstants.ENDPOINT_SECURITY_TYPE_SIGNED);

		} catch (ParseException e) {

			System.out.println(e.getMessage());
			formatter.printHelp("utility-name", options);
			System.exit(1);
		}

		TradeStrategy tradeStrategy = new TradeStrategy(altcoin, runmode, apikey, secretkey);
		tradeStrategy.executeStrategy();

	}

}
