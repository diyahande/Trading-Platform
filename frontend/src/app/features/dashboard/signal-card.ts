import { AfterViewChecked, Component, ElementRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import Chart from 'chart.js/auto';

interface SignalMetrics {
  ma20: number;
  ma50: number;
  rsi14: number;
  price: number;
  priceChange: number;
  priceChangePercent: number;
  high52Week?: number;
  low52Week?: number;
  percentile?: string;
}

export interface SignalResponse {
  symbol: string;
  direction: string;           // "BUY", "SELL", "HOLD"
  strength: number;             // 0-100
  rationale: string;             // Plain-English explanation
  computedAt: string;            // ISO timestamp
  validUntil: string;            // ISO timestamp
  staleness: string;             // "FRESH", "AGING", "STALE"
  metrics: SignalMetrics | null;
  priceHistory90d?: Array<{ date: string; close: number }>;
  methodology: string;
}

/**
 * Trading signal recommendation card.
 * Displays a recommendation (BUY/SELL/HOLD) with:
 * - Direction and strength confidence (0-100)
 * - Plain-English rationale
 * - Detailed metrics (MA20, MA50, RSI, current price)
 * - Validity window and staleness indicator
 * - Methodology and disclaimer
 */
@Component({
  selector: 'app-signal-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './signal-card.html',
  styleUrls: ['./signal-card.css']
})
export class SignalCardComponent implements OnInit, OnChanges, AfterViewChecked, OnDestroy {
  @Input() symbol!: string;
  @Input() prefetchedSignal: SignalResponse | null = null;
  @ViewChild('priceChart') priceChartRef?: ElementRef<HTMLCanvasElement>;

  signal: SignalResponse | null = null;
  loading = false;
  error: string | null = null;
  private chart: Chart | null = null;
  private chartReadyForSignal = false;
  private chartRenderedForSymbol: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    if (this.applyPrefetchedSignal()) {
      return;
    }

    if (this.symbol) {
      this.loadSignal();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    const symbolChange = changes['symbol'];
    if (!symbolChange) {
      return;
    }

    const nextSymbol = (symbolChange.currentValue ?? '').toString().trim();
    const previousSymbol = (symbolChange.previousValue ?? '').toString().trim();
    if (!nextSymbol || nextSymbol === previousSymbol) {
      this.applyPrefetchedSignal();
      return;
    }

    if (this.applyPrefetchedSignal()) {
      return;
    }

    this.loadSignal();
  }

  private applyPrefetchedSignal(): boolean {
    if (!this.prefetchedSignal) {
      return false;
    }

    const prefetchedSymbol = (this.prefetchedSignal.symbol ?? '').toUpperCase();
    const currentSymbol = (this.symbol ?? '').toUpperCase();
    if (!prefetchedSymbol || prefetchedSymbol !== currentSymbol) {
      return false;
    }

    this.destroyChart();
    this.signal = this.prefetchedSignal;
    this.loading = false;
    this.error = null;
    this.chartReadyForSignal = false;
    this.chartRenderedForSymbol = null;
    return true;
  }

  ngAfterViewChecked(): void {
    if (!this.signal || !this.hasPriceHistory() || !this.priceChartRef?.nativeElement) {
      return;
    }

    const currentSymbol = this.signal.symbol;
    if (this.chartReadyForSignal && this.chartRenderedForSymbol === currentSymbol) {
      return;
    }

    this.renderPriceChart();
  }

  ngOnDestroy(): void {
    this.destroyChart();
  }

  private loadSignal(): void {
    this.destroyChart();
    this.signal = null;
    this.loading = true;
    this.error = null;
    this.chartReadyForSignal = false;
    this.chartRenderedForSymbol = null;

    this.http.get<SignalResponse>(`/api/v1/signals/${this.symbol}`)
      .subscribe({
        next: (response) => {
          // Defers state mutation to next macrotask to avoid NG0100 in dev mode
          setTimeout(() => {
            this.signal = response;
            this.loading = false;
            this.chartReadyForSignal = false;
            this.chartRenderedForSymbol = null;
            this.destroyChart();
          }, 0);
        },
        error: (err) => {
          console.error('Error loading signal:', err);
          setTimeout(() => {
            this.error = 'Failed to load signal recommendation. Please try again.';
            this.loading = false;
          }, 0);
        }
      });
  }

  getCardClass(): string {
    if (!this.signal) return '';
    return `signal-${this.signal.direction.toLowerCase()}`;
  }

  getDirectionClass(): string {
    if (!this.signal) return '';
    return `direction-${this.signal.direction.toLowerCase()}`;
  }

  getDirectionIcon(): string {
    if (!this.signal) return '';
    switch (this.signal.direction) {
      case 'BUY': return '🟢';
      case 'SELL': return '🔴';
      default: return '🟡';
    }
  }

  getSignalName(): string {
    if (!this.signal) return '';
    switch (this.signal.direction) {
      case 'BUY': return 'Bullish Signal - Consider Buying';
      case 'SELL': return 'Bearish Signal - Consider Selling';
      default: return 'Neutral Signal - Hold Position';
    }
  }

  formatTime(isoTimestamp: string): string {
    const date = new Date(isoTimestamp);
    return date.toLocaleTimeString('en-US', { 
      hour: '2-digit', 
      minute: '2-digit',
      second: '2-digit',
      hour12: true 
    });
  }

  hasPriceHistory(): boolean {
    return (this.signal?.priceHistory90d?.length ?? 0) >= 2;
  }

  private renderPriceChart(): void {
    if (!this.signal?.priceHistory90d || !this.priceChartRef?.nativeElement) {
      return;
    }

    const points = this.signal.priceHistory90d;
    const labels = points.map((point) => {
      const date = new Date(point.date);
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    });
    const values = points.map((point) => point.close);
    const lineColor = '#4f8cff';

    this.destroyChart();

    this.chart = new Chart(this.priceChartRef.nativeElement, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: `${this.signal.symbol} Close`,
            data: values,
            borderColor: lineColor,
            backgroundColor: `${lineColor}1f`,
            fill: true,
            tension: 0.25,
            pointRadius: 0,
            pointHoverRadius: 4,
            borderWidth: 2,
            pointHoverBackgroundColor: '#d7e5ff',
            pointHoverBorderColor: '#4f8cff',
            pointHoverBorderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index',
          intersect: false,
        },
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            backgroundColor: '#0f1d2a',
            titleColor: '#d7e5ff',
            bodyColor: '#d7e5ff',
            borderColor: 'rgba(79, 140, 255, 0.4)',
            borderWidth: 1,
            callbacks: {
              label: (context) => {
                const value = context.parsed.y;
                if (typeof value !== 'number') {
                  return 'Close: -';
                }
                return `Close: $${value.toFixed(2)}`;
              },
            },
          },
        },
        scales: {
          x: {
            grid: { color: 'rgba(79, 140, 255, 0.12)' },
            ticks: {
              color: '#9fb2c6',
              maxTicksLimit: 6,
            },
          },
          y: {
            ticks: {
              color: '#9fb2c6',
              callback: (value) => `$${value}`,
            },
            grid: {
              color: 'rgba(79, 140, 255, 0.12)',
            },
          },
        },
      },
    });

    this.chartReadyForSignal = true;
    this.chartRenderedForSymbol = this.signal.symbol;
  }

  private destroyChart(): void {
    if (this.chart) {
      this.chart.destroy();
      this.chart = null;
    }
  }
}
