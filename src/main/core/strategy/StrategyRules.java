package main.core.strategy;

import java.time.LocalTime;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Rule;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.statistics.StandardDeviationIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.indicators.trackers.bollinger.BollingerBandsLowerIndicator;
import eu.verdelhan.ta4j.indicators.trackers.bollinger.BollingerBandsMiddleIndicator;
import eu.verdelhan.ta4j.indicators.trackers.bollinger.BollingerBandsUpperIndicator;
import eu.verdelhan.ta4j.trading.rules.OverIndicatorRule;
import eu.verdelhan.ta4j.trading.rules.UnderIndicatorRule;
import main.core.indicators.EMAIndicator;
import main.core.indicators.WilderRSIIndicator;
import main.core.parameters.RobotParameters;
import main.core.parameters.daytrade.DayTradeParameters;
import main.core.parameters.entry.BollingerBandsParameters;
import main.core.parameters.entry.MovingAverageParameters;
import main.core.parameters.entry.RSIParameters;
import main.core.parameters.exit.ExitParameters;
import main.core.parameters.exit.FixedStopGainParameters;
import main.core.parameters.exit.FixedStopLossParameters;
import main.core.parameters.exit.TrailingStopGainParameters;
import main.core.rules.AllowOpenRule;
import main.core.rules.ForceCloseRule;
import main.core.rules.NoExitRule;
import main.core.rules.stops.FixedStopGainRule;
import main.core.rules.stops.FixedStopLossRule;
import main.core.rules.stops.TrailingStopRule;

public class StrategyRules {
	private final ClosePriceIndicator prices;
	private final RobotParameters parameters;

	private final Strategy buyStrategy;
	private final Strategy sellStrategy;

	private Rule buyEntryRule;
	private Rule buyExitRule;
	private Rule sellEntryRule;
	private Rule sellExitRule;

	private FixedStopLossRule stopLossRule;
	private FixedStopGainRule stopGainRule;
	private TrailingStopRule trailingStopRule;

	public StrategyRules(ClosePriceIndicator prices, RobotParameters parameters) {
		this.prices = prices;
		this.parameters = parameters;

		this.setRules();

		buyStrategy = new Strategy(buyEntryRule, buyExitRule);
		sellStrategy = new Strategy(sellEntryRule, sellExitRule);
	}

	private void setRules() {
		this.setEntryRules();
		this.setExitRules();
		this.setDayTradeRules();
	}

	private void setEntryRules() {
		this.setMovingAverageRules();
		this.setRSIRules();
		this.setBBRules();
	}

	private void setExitRules() {
		this.setFixedStopLoss();
		this.setFixedStopGain();
		this.setTrailingStop();
	}

	private void setDayTradeRules() {
		DayTradeParameters dayTradeParam = parameters.getDayTradeParameters();
		if (dayTradeParam == null) {
			return;
		}

		LocalTime initialEntry = dayTradeParam.getInitialEntryTimeLimit();
		LocalTime finalEntry = dayTradeParam.getFinalEntryTimeLimit();
		LocalTime exit = dayTradeParam.getExitTimeLimit();

		Rule allowOpenRule = new AllowOpenRule(prices.getTimeSeries(), initialEntry, finalEntry);
		Rule forceCloseRule = new ForceCloseRule(prices.getTimeSeries(), exit);

		buyEntryRule = buyEntryRule == null ? allowOpenRule : buyEntryRule.and(allowOpenRule);
		setBuyExitRule(forceCloseRule);

		sellEntryRule = sellEntryRule == null ? allowOpenRule : sellEntryRule.and(allowOpenRule);
		setSellExitRule(forceCloseRule);
	}

	private void setMovingAverageRules() {
		MovingAverageParameters param = parameters.getEntryParameters().getMovingAverageParameters();
		if (param == null)
			return;

		Indicator<Decimal> shortMovingAverage;
		Indicator<Decimal> longMovingAverage;

		switch (param.getType()) {
		case SIMPLE:
			shortMovingAverage = new SMAIndicator(prices, param.getShortPeriods());
			longMovingAverage = new SMAIndicator(prices, param.getLongPeriods());
			break;

		case EXPONENTIAL:
			shortMovingAverage = new EMAIndicator(prices, param.getShortPeriods());
			longMovingAverage = new EMAIndicator(prices, param.getLongPeriods());
			break;

		default:
			throw new IllegalArgumentException("Invalid moving average type");
		}

		Rule underRule = new UnderIndicatorRule(shortMovingAverage, longMovingAverage);
		Rule overRule = new OverIndicatorRule(shortMovingAverage, longMovingAverage);

		buyEntryRule = buyEntryRule == null ? underRule : buyEntryRule.and(underRule);
		setBuyExitRule(overRule);

		sellEntryRule = sellEntryRule == null ? overRule : sellEntryRule.and(overRule);
		setSellExitRule(underRule);
	}

	private void setRSIRules() {
		RSIParameters param = parameters.getEntryParameters().getRsiParameters();
		if (param == null)
			return;

		WilderRSIIndicator rsiIndicator = new WilderRSIIndicator(prices, param.getPeriods());

		Decimal lowerLimit = Decimal.valueOf(param.getLowerValue());
		Decimal upperLimit = Decimal.valueOf(param.getUpperValue());

		Rule underRule = new UnderIndicatorRule(rsiIndicator, lowerLimit);
		Rule overRule = new OverIndicatorRule(rsiIndicator, upperLimit);

		buyEntryRule = buyEntryRule == null ? underRule : buyEntryRule.and(underRule);
		setBuyExitRule(overRule);

		sellEntryRule = sellEntryRule == null ? overRule : sellEntryRule.and(overRule);
		setSellExitRule(underRule);
	}

	private void setBBRules() {
		BollingerBandsParameters param = parameters.getEntryParameters().getBollingerBandsParameters();
		if (param == null)
			return;

		SMAIndicator simpleMovingAverage = new SMAIndicator(prices, param.getPeriods());
		StandardDeviationIndicator stdDeviation = new StandardDeviationIndicator(prices, param.getPeriods());

		BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(simpleMovingAverage);
		BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, stdDeviation, param.getFactor());
		BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, stdDeviation, param.getFactor());

		Rule underRule = new UnderIndicatorRule(prices, lower);
		Rule overRule = new OverIndicatorRule(prices, upper);

		buyEntryRule = buyEntryRule == null ? underRule : buyEntryRule.and(underRule);
		setBuyExitRule(overRule);

		sellEntryRule = sellEntryRule == null ? overRule : sellEntryRule.and(overRule);
		setSellExitRule(underRule);
	}

	private void setFixedStopLoss() {
		ExitParameters exitParam = parameters.getExitParameters();
		if (exitParam == null)
			return;

		FixedStopLossParameters fixedStopLoss = exitParam.getFixedStopLoss();
		if (fixedStopLoss == null)
			return;

		stopLossRule = new FixedStopLossRule(prices, fixedStopLoss.getValue(), fixedStopLoss.getType());
	}

	private void setFixedStopGain() {
		ExitParameters exitParam = parameters.getExitParameters();
		if (exitParam == null)
			return;

		FixedStopGainParameters fixedStopGain = exitParam.getFixedStopGain();
		if (fixedStopGain == null)
			return;

		stopGainRule = new FixedStopGainRule(prices, fixedStopGain.getValue(), fixedStopGain.getType());
	}

	private void setTrailingStop() {
		ExitParameters exitParam = parameters.getExitParameters();
		if (exitParam == null)
			return;

		TrailingStopGainParameters trailingStop = exitParam.getTrailingStopGain();
		if (trailingStop == null)
			return;

		trailingStopRule = new TrailingStopRule(prices, trailingStop.getTrigger(), trailingStop.getDistance(),
				trailingStop.getType());
	}

	public Decimal buyOperate(int index, TradingRecord tradingRecord) {
		if (stopLossRule != null && stopLossRule.isSatisfied(index, tradingRecord)) {
			return stopLossRule.getExitPrice(tradingRecord);
		}

		if (stopGainRule != null && stopGainRule.isSatisfied(index, tradingRecord)) {
			return stopGainRule.getExitPrice(tradingRecord);
		}

		if (trailingStopRule != null && trailingStopRule.isSatisfied(index, tradingRecord)) {
			return trailingStopRule.getExitPrice(tradingRecord);
		}

		if (buyStrategy.shouldOperate(index, tradingRecord)) {
			return prices.getValue(index);
		}

		return null;
	}

	public Decimal sellOperate(int index, TradingRecord tradingRecord) {
		if (stopLossRule != null && stopLossRule.isSatisfied(index, tradingRecord)) {
			return stopLossRule.getExitPrice(tradingRecord);
		}

		if (stopGainRule != null && stopGainRule.isSatisfied(index, tradingRecord)) {
			return stopGainRule.getExitPrice(tradingRecord);
		}

		if (trailingStopRule != null && trailingStopRule.isSatisfied(index, tradingRecord)) {
			return trailingStopRule.getExitPrice(tradingRecord);
		}

		if (sellStrategy.shouldOperate(index, tradingRecord)) {
			return prices.getValue(index);
		}

		return null;
	}

	private void setBuyExitRule(Rule exitRule) {
		switch (parameters.getExitParameters().getExitType()) {
		case ANY_INDICATOR:
			buyExitRule = buyExitRule == null ? exitRule : buyExitRule.or(exitRule);
			break;
		case ALL_INDICATORS:
			buyExitRule = buyExitRule == null ? exitRule : buyExitRule.and(exitRule);
			break;
		case NO_INDICATORS:
			buyExitRule = new NoExitRule();
			break;
		default:
			break;
		}

	}

	private void setSellExitRule(Rule exitRule) {
		switch (parameters.getExitParameters().getExitType()) {
		case ANY_INDICATOR:
			sellExitRule = sellExitRule == null ? exitRule : sellExitRule.or(exitRule);
			break;
		case ALL_INDICATORS:
			sellExitRule = sellExitRule == null ? exitRule : sellExitRule.and(exitRule);
			break;
		case NO_INDICATORS:
			sellExitRule = new NoExitRule();
			break;
		default:
			break;
		}
	}

	public void startNewTrade() {
		if (trailingStopRule != null) {
			this.trailingStopRule.startNewTrade();
		}
	}

}
