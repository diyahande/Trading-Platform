import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { APP_ENV } from '../config/app-env.generated';

type FrankfurterRatesResponse = {
  amount?: number;
  base?: string;
  date?: string;
  rates: Record<string, number>;
};

type FrankfurterRateRow = {
  date?: string;
  base?: string;
  quote?: string;
  rate?: number;
};

type FrankfurterApiResponse = FrankfurterRatesResponse | FrankfurterRateRow[];

export type QuoteToUsdEstimate = {
  tradeTotal: number;
  tradeCurrency: string;
  usdTotal: number;
  ratePerUsd: number;
};

@Injectable({
  providedIn: 'root',
})
export class CurrencyConversion {
  constructor(private readonly http: HttpClient) {}

  async quoteTotalToUsd(tradeTotal: number, tradeCurrency: string): Promise<QuoteToUsdEstimate> {
    if (!Number.isFinite(tradeTotal) || tradeTotal <= 0) {
      throw new Error('Invalid trade total');
    }

    const currency = tradeCurrency.trim().toUpperCase();
    if (currency === 'USD') {
      return {
        tradeTotal,
        tradeCurrency: 'USD',
        usdTotal: tradeTotal,
        ratePerUsd: 1,
      };
    }

    const endpoint = APP_ENV.ratesUrl;
    const response = await firstValueFrom(this.http.get<FrankfurterApiResponse>(endpoint));
    const ratePerUsd = this.readRateFromResponse(response, currency);

    if (!Number.isFinite(ratePerUsd) || ratePerUsd <= 0) {
      throw new Error('Rate unavailable');
    }

    return {
      tradeTotal,
      tradeCurrency: currency,
      usdTotal: tradeTotal / ratePerUsd,
      ratePerUsd,
    };
  }

  private readRateFromResponse(response: FrankfurterApiResponse, currency: string): number {
    if (Array.isArray(response)) {
      const row = response.find(
        (item) => item?.quote?.toUpperCase() === currency && Number.isFinite(item?.rate),
      );
      return row?.rate ?? Number.NaN;
    }

    return response?.rates?.[currency] ?? Number.NaN;
  }
}
