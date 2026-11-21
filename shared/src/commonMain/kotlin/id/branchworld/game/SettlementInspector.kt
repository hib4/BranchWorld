package id.branchworld.game

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.shape_site_founded
import branchworld.shared.generated.resources.shape_site_shortage
import branchworld.shared.generated.resources.shape_site_expanded
import branchworld.shared.generated.resources.shape_site_relocated
import branchworld.shared.generated.resources.shape_site_abandoned
import branchworld.shared.generated.resources.shape_welfare
import branchworld.shared.generated.resources.shape_welfare_strain
import branchworld.shared.generated.resources.settlement_abandoned
import branchworld.shared.generated.resources.settlement_abandoned_now
import branchworld.shared.generated.resources.settlement_balance
import branchworld.shared.generated.resources.settlement_expanded
import branchworld.shared.generated.resources.settlement_founded
import branchworld.shared.generated.resources.settlement_inspector
import branchworld.shared.generated.resources.settlement_labor
import branchworld.shared.generated.resources.settlement_relocated
import branchworld.shared.generated.resources.settlement_residents
import branchworld.shared.generated.resources.settlement_searching
import branchworld.shared.generated.resources.settlement_shortage
import branchworld.shared.generated.resources.settlement_shortage_now
import branchworld.shared.generated.resources.settlement_site
import branchworld.shared.generated.resources.settlement_stocks
import id.branchworld.game.ecology.SettlementEvent
import id.branchworld.game.ecology.SettlementEventKind
import id.branchworld.game.ecology.SettlementWorld
import id.branchworld.game.ecology.region
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettlementInspector(world: SettlementWorld, showHeading: Boolean = true) {
    if (showHeading) {
        Text(stringResource(Res.string.settlement_inspector), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() })
    }
    if (world.sites.isEmpty()) Text(stringResource(Res.string.settlement_searching))
    for (site in world.sites) {
        Text(stringResource(Res.string.settlement_site, site.id, regionName(site.center.region())))
        Text(stringResource(Res.string.settlement_residents, site.residents, site.homes))
        if (site.residents == 0) {
            Text(stringResource(Res.string.settlement_abandoned_now))
            continue
        }
        Text(stringResource(Res.string.settlement_stocks, site.food, site.wood, site.stone))
        Text(stringResource(Res.string.settlement_labor, site.labor.farm, site.labor.wood, site.labor.stone))
        val balance = if (site.foodBalance > 0) "+${site.foodBalance}" else site.foodBalance.toString()
        Text(stringResource(Res.string.settlement_balance, balance))
        site.welfare?.let {
            Text(stringResource(Res.string.shape_welfare, it.water, it.cropYield))
            if (it.unmet > 0) Text(stringResource(Res.string.shape_welfare_strain))
        }
        if (site.shortage > 0) Text(stringResource(Res.string.settlement_shortage_now))
    }
}

@Composable
internal fun SettlementEvent.label(showTick: Boolean = true): String {
    if (!showTick) return when (kind) {
        SettlementEventKind.Founded -> stringResource(Res.string.shape_site_founded)
        SettlementEventKind.Shortage -> stringResource(Res.string.shape_site_shortage)
        SettlementEventKind.Expanded -> stringResource(Res.string.shape_site_expanded, amount)
        SettlementEventKind.Relocated -> stringResource(Res.string.shape_site_relocated)
        SettlementEventKind.Abandoned -> stringResource(Res.string.shape_site_abandoned)
    }
    val time = tick.toString()
    return when (kind) {
        SettlementEventKind.Founded -> stringResource(Res.string.settlement_founded, time, siteId, amount)
        SettlementEventKind.Shortage -> stringResource(Res.string.settlement_shortage, time, siteId, amount)
        SettlementEventKind.Expanded -> stringResource(Res.string.settlement_expanded, time, siteId, amount)
        SettlementEventKind.Relocated -> stringResource(Res.string.settlement_relocated, time, siteId, amount)
        SettlementEventKind.Abandoned -> stringResource(Res.string.settlement_abandoned, time, siteId)
    }
}
