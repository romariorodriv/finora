import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-agent-hub',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="agent-hub">
      <header class="agent-hero">
        <span class="agent-label">BIENVENIDO A SARELA</span>
        <h1>Tu dinero tiene metas.<br>Tenemos agentes para ayudarte.</h1>
        <p>Elige qué necesitas resolver. Cada agente se especializa en investigar alternativas y ayudarte a decidir sin gastar de más.</p>
      </header>
      <div class="agent-grid">
        <article class="agent-tile featured">
          <span class="agent-tag">DISPONIBLE EN BETA</span>
          <div class="agent-emoji" aria-hidden="true">⌂</div>
          <h2>Sarela Vivienda</h2><p>Analiza cuánto podrías pagar y consulta opciones para encontrar tu primer departamento.</p>
          <button type="button" (click)="select.emit('housing')">Explorar viviendas <span aria-hidden="true">→</span></button>
        </article>
        <article class="agent-tile">
          <span class="agent-tag muted">PRÓXIMAMENTE</span>
          <div class="agent-emoji" aria-hidden="true">▱</div>
          <h2>Sarela Auto</h2><p>Compara el costo real de comprar y mantener un vehículo.</p>
          <button type="button" (click)="select.emit('auto')">Conocer el agente <span aria-hidden="true">→</span></button>
        </article>
        <article class="agent-tile">
          <span class="agent-tag muted">GMAIL · ACCESO CON PERMISO</span>
          <div class="agent-emoji" aria-hidden="true">◫</div>
          <h2>Sarela Gastos</h2><p>Revisa tus movimientos y las herramientas financieras existentes.</p>
          <button type="button" (click)="select.emit('dashboard')">Ir a mis gastos <span aria-hidden="true">→</span></button>
        </article>
        <article class="agent-tile">
          <span class="agent-tag muted">EN PREPARACIÓN</span>
          <div class="agent-emoji" aria-hidden="true">₴</div>
          <h2>Sarela Deudas</h2><p>En el futuro analizará obligaciones y estrategias de pago con información autorizada.</p>
          <button type="button" (click)="select.emit('debts')">Conocer el agente <span aria-hidden="true">→</span></button>
        </article>
      </div>
      <p class="agent-privacy">Gmail solo se solicita para las funciones que lo necesitan. Vivienda y Auto no necesitan acceso a tus correos.</p>
    </section>
  `,
  styles: [`
    .agent-hub{max-width:1180px;margin:auto;padding:8px 0 60px}
    .agent-hero{background:linear-gradient(120deg,#103d2c,#1b6b47);color:#fff;border-radius:24px;padding:clamp(25px,5vw,64px);margin-bottom:26px}
    .agent-label{letter-spacing:.16em;color:#c3ead2;font-size:11px;font-weight:800}
    .agent-hero h1{font-size:clamp(27px,3.7vw,48px);line-height:1.16;letter-spacing:-.035em;margin:15px 0 16px}
    .agent-hero p{color:#d7e9dd;max-width:660px;line-height:1.6;font-size:15px}
    .agent-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}
    .agent-tile{display:flex;flex-direction:column;align-items:flex-start;background:white;border:1px solid #dfebe2;padding:27px;border-radius:18px;min-height:245px}
    .agent-tile.featured{background:#eff8f0;border-color:#b7dbc2}.agent-tag{font-size:10px;letter-spacing:.09em;font-weight:800;color:#1f7645}
    .agent-tag.muted{color:#718278}.agent-emoji{font-size:28px;color:#237847;margin:18px 0 10px}
    .agent-tile h2{font-size:22px;letter-spacing:-.03em;margin:0 0 9px}.agent-tile p{font-size:13px;color:#5b6e61;line-height:1.6;flex:1}
    .agent-tile button{border:0;background:none;color:#17683e;font-weight:800;padding:12px 0 0;cursor:pointer}
    .agent-privacy{margin:22px 0;font-size:12px;color:#63766a}
    @media(max-width:700px){.agent-grid{grid-template-columns:1fr}.agent-tile{min-height:195px}.agent-hero{border-radius:16px}}
  `]
})
export class AgentHubComponent {
  @Output() select = new EventEmitter<'housing'|'auto'|'dashboard'|'debts'>();
}
