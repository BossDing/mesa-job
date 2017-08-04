package com.di.mesa.job.jstorm.bean;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import java.io.Serializable;

/**
 * Created by davi on 17/8/3.
 */
public class TradeCustomer implements Serializable {

    private static final long serialVersionUID = 1294530416638900059L;

    protected final long timestamp = System.currentTimeMillis();
    protected Pair trade;
    protected Pair customer;

    public Pair getTrade() {
        return trade;
    }

    public void setTrade(Pair trade) {
        this.trade = trade;
    }

    public Pair getCustomer() {
        return customer;
    }

    public void setCustomer(Pair customer) {
        this.customer = customer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
