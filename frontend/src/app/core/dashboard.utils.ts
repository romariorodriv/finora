import { DashboardResponse } from './models/api.models';

export interface DailySpendingPoint {
  date: string;
  label: string;
  value: number;
}

export interface CategorySpendingPoint {
  name: string;
  macroCategory?: string;
  merchant?: string;
  value: number;
  percentage: number;
  color: string;
}

export function normalizeDashboard(response: DashboardResponse): DashboardResponse {
  return {
    month: response.month,
    expenses: Number(response.expenses),
    income: Number(response.income),
    balance: Number(response.balance),
    dailyAverage: Number(response.dailyAverage),
    projection: Number(response.projection),
    categories: response.categories ?? {},
    macroCategories: (response.macroCategories ?? []).map(item => ({
      ...item,
      total: Number(item.total),
      percentage: Number(item.percentage)
    })),
    merchants: (response.merchants ?? []).map(item => ({
      ...item,
      total: Number(item.total),
      percentage: Number(item.percentage)
    })),
    insightTitle: response.insightTitle,
    insightBody: response.insightBody,
    daily: response.daily ?? {},
    transactions: response.transactions ?? [],
    recurring: response.recurring ?? []
  };
}

export function calculateDailySpending(daily: Record<string, number>, range: 7 | 30): DailySpendingPoint[] {
  const monthLabels = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  return Object.entries(daily)
    .map(([date, value]) => {
      const [, month, day] = date.split('-').map(Number);
      return {
        date,
        label: `${String(day).padStart(2, '0')} ${monthLabels[month - 1]}`,
        value: Number(value)
      };
    })
    .sort((a, b) => a.date.localeCompare(b.date))
    .slice(-range);
}

export function calculateCategoryDistribution(
  categories: Record<string, number> | undefined,
  colors: string[]
): CategorySpendingPoint[] {
  const entries = Object.entries(categories ?? {})
    .map(([name, value]) => ({ name, value: Number(value) }))
    .sort((a, b) => b.value - a.value);
  const total = entries.reduce((sum, item) => sum + item.value, 0);

  return entries.map((item, index) => ({
    ...item,
    percentage: total ? item.value / total * 100 : 0,
    color: colors[index % colors.length]
  }));
}

export function calculateMacroCategoryDistribution(
  categories: { macroCategory: string; label: string; total: number; percentage: number }[] | undefined,
  colors: string[]
): CategorySpendingPoint[] {
  return (categories ?? [])
    .filter(item => Number(item.total) > 0)
    .map((item, index) => ({
      name: item.label,
      macroCategory: item.macroCategory,
      value: Number(item.total),
      percentage: Number(item.percentage),
      color: colors[index % colors.length]
    }));
}

export function calculateMerchantDistribution(
  merchants: { merchant: string; label: string; total: number; percentage: number }[] | undefined,
  colors: string[]
): CategorySpendingPoint[] {
  return (merchants ?? [])
    .filter(item => Number(item.total) > 0)
    .map((item, index) => ({
      name: item.label,
      merchant: item.merchant,
      value: Number(item.total),
      percentage: Number(item.percentage),
      color: colors[index % colors.length]
    }));
}

export function generateFinancialInsight(categories: CategorySpendingPoint[], expenses: number): string {
  if (!categories.length || expenses === 0) {
    return 'No tenemos suficientes movimientos todavia para darte una recomendacion.';
  }
  const largest = categories[0];
  if (largest.macroCategory === 'OTROS' || largest.name.toLocaleLowerCase('es') === 'otros') {
    return 'Hay movimientos que Sarela todavia no pudo clasificar.';
  }
  return `${largest.name} lidera tus gastos y representa el ${Math.round(largest.percentage)}% de tus gastos del mes.`;
}
