import { DashboardResponse } from './models/api.models';

export interface DailySpendingPoint {
  date: string;
  label: string;
  value: number;
}

export interface CategorySpendingPoint {
  name: string;
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
  categories: Record<string, number>,
  colors: string[]
): CategorySpendingPoint[] {
  const entries = Object.entries(categories)
    .map(([name, value]) => ({ name, value: Number(value) }))
    .sort((a, b) => b.value - a.value);
  const total = entries.reduce((sum, item) => sum + item.value, 0);
  const visible = entries.slice(0, 5);
  const remainder = entries.slice(5).reduce((sum, item) => sum + item.value, 0);

  if (remainder > 0) {
    const existingOther = visible.find(item => item.name.toLocaleLowerCase('es') === 'otros');
    if (existingOther) {
      existingOther.value += remainder;
    } else {
      visible.push({ name: 'Otros', value: remainder });
    }
  }

  return visible.map((item, index) => ({
    ...item,
    percentage: total ? item.value / total * 100 : 0,
    color: colors[index % colors.length]
  }));
}

export function generateFinancialInsight(categories: CategorySpendingPoint[], expenses: number): string {
  if (!categories.length || expenses === 0) {
    return 'No tenemos suficientes movimientos todavía para darte una recomendación.';
  }
  const largest = categories[0];
  return `${largest.name} es tu mayor categoría y representa el ${Math.round(largest.percentage)}% de tus gastos del mes.`;
}
