package com.charles.java;


import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 3. 6..
 */

public class Question3 {

	public static void main(String[] argh) {
		double payment = 12324.13d;
		DecimalFormat usdf = new DecimalFormat("#,##0.00");
		String us = String.format("$%s", usdf.format(payment).toString());
		String india = String.format("RS.%s", usdf.format(payment).toString());
		String china = String.format("￥%s", usdf.format(payment).toString());
		DecimalFormat frdf = new DecimalFormat("#,##0.00");
		String france = String.format("%s €", frdf.format(payment));
		france = france.replaceAll(",", " ");
		france = france.replaceAll("\\.", ",");


		us = NumberFormat.getCurrencyInstance(Locale.US).format(payment);
		india = NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(payment);
		china = NumberFormat.getCurrencyInstance(Locale.CHINA).format(payment);
		france = NumberFormat.getCurrencyInstance(Locale.FRANCE).format(payment);
		System.out.println("US: " + us);
		System.out.println("India: " + india);
		System.out.println("China: " + china);
		System.out.println("France: " + france);

	}

}
