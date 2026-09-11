import {
  calculateCategoryDistribution,
  calculateDailySpending,
  generateFinancialInsight,
  normalizeDashboard
} from './dashboard.utils';
import { DashboardResponse } from './models/api.models';

describe('dashboard transformations', () => {
  const fixture: DashboardResponse = {
    month: '2026-09',
    expenses: 7263.62,
    income: 0,
    balance: -7263.62,
    dailyAverage: 660.33,
    projection: 19809.90,
    categories: { Comida: 38.90, Suscripciones: 131.60, Otros: 7093.12 },
    daily: { '2026-09-10': 271.60, '2026-09-09': 32.90 }
  };
  const colors = ['#0f3d30', '#276f5b', '#48b88e'];

  it('preserves the backend totals without replacing valid zeroes', () => {
    const dashboard = normalizeDashboard(fixture);

    expect(dashboard.expenses).toBe(7263.62);
    expect(dashboard.income).toBe(0);
    expect(dashboard.balance).toBe(-7263.62);
    expect(dashboard.dailyAverage).toBe(660.33);
    expect(dashboard.projection).toBe(19809.90);
  });

  it('converts the daily record to ascending chart points', () => {
    const points = calculateDailySpending(fixture.daily, 30);

    expect(points.map(point => point.date)).toEqual(['2026-09-09', '2026-09-10']);
    expect(points.map(point => point.label)).toEqual(['09 sep', '10 sep']);
    expect(points.map(point => point.value)).toEqual([32.90, 271.60]);
  });

  it('converts categories and generates an insight from real totals', () => {
    const categories = calculateCategoryDistribution(fixture.categories, colors);

    expect(categories.map(item => item.name)).toEqual(['Otros', 'Suscripciones', 'Comida']);
    expect(categories.map(item => item.value)).toEqual([7093.12, 131.60, 38.90]);
    expect(generateFinancialInsight(categories, fixture.expenses)).toContain('Otros');
  });
});
