package com.charles.java;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {

	private static int MAX_POINT = 10;
	private static int MIN_POINT = 0;


	public static void main(String[] args) {
		System.out.println("start");
		Scanner scanner = new Scanner(System.in);
//		String input = scanner.nextLine();
		String input = "1D2S3T*";
		Pattern numberPattern = Pattern.compile("(^[0-9]*$)");

		ArrayList<Integer> scores = new ArrayList<>(3);

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (numberPattern.matcher(Character.toString(c)).matches()) {
				//숫자
				if (c == '1') {
					if (i + 1 != input.length() && input.charAt(i + 1) == '0') { //점수가 10일 때
						scores.add(10);
						i++;
						continue;
					}
				}
				scores.add(Integer.valueOf(String.valueOf(c)));
			} else {
				//숫자 아닐 때
				int recentIndex = scores.size() - 1;
				int recentScore = scores.get(recentIndex);//최근 숫자
				switch (c) {
					case 'S':
						scores.set(recentIndex, (int) Math.pow(recentScore, 1));
						break;
					case 'D':
						scores.set(recentIndex, (int) Math.pow(recentScore, 2));
						break;
					case 'T':
						scores.set(recentIndex, (int) Math.pow(recentScore, 3));
						break;
					case '*':
						scores.set(recentIndex, recentScore * 2);
						if (recentIndex >= 1) {
							scores.set(recentIndex - 1, scores.get(recentIndex - 1) * 2);
						}
						break;
					case '#':
						scores.set(recentIndex, recentScore * -1);
						break;
				}
			}
		}

		int result = 0;
		for (int i : scores) {
			result += i;
			System.out.println(i);
		}

		System.out.println(result);

		System.out.println("end");
	}
}
