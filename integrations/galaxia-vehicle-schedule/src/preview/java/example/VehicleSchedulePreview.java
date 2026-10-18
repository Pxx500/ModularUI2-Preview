package example;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.VehicleScheduleUI;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.gtnewhorizons.galaxia.client.EnumColors;

import dev.modularui.preview.PreviewEntrypoint;

/** Loads the production builder. Only representative client state and local callbacks live here. */
public class VehicleSchedulePreview implements PreviewEntrypoint {
    protected String state() { return "filled"; }
    @Override public String owner() { return "galaxia"; }
    @Override public Class<?> previewedClass() { return VehicleScheduleUI.class; }

    @Override public Object createPanel(Context context) {
        var earth = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        var luna = CelestialObjectKey.registered(CelestialObjectId.MOON);
        var endpoint = UUID.fromString("20000000-0000-0000-0000-000000000001");
        var station = UUID.fromString("50000000-0000-0000-0000-000000000001");
        boolean silo = state().startsWith("silo") || state().equals("invalid-refuel");
        boolean orbitalEndpoint = state().equals("station-pad") || state().equals("dock");
        var endpointContext = orbitalEndpoint ? VehicleSchedule.Context.ORBIT : VehicleSchedule.Context.SURFACE;
        var endpointKind = silo ? Kind.SILO : state().equals("dock") ? Kind.DOCK : Kind.LANDING_PAD;
        var a = new VehicleSchedule.Visit(UUID.fromString("10000000-0000-0000-0000-000000000001"),
            new VehicleSchedule.Place(earth, endpointContext, new VehicleSchedule.Exact(endpoint, endpointKind)),
            silo ? List.of(state().equals("silo-exact")
                ? new VehicleSchedule.Refuel(UUID.fromString("30000000-0000-0000-0000-000000000001"), Map.of())
                : VehicleSchedule.Refuel.full(UUID.fromString("30000000-0000-0000-0000-000000000001")))
                : state().equals("dock") ? List.of() : List.of(new VehicleSchedule.Repair(UUID.fromString("30000000-0000-0000-0000-000000000001"), 50)));
        var b = new VehicleSchedule.Visit(UUID.fromString("10000000-0000-0000-0000-000000000002"),
            new VehicleSchedule.Place(state().startsWith("risk") ? luna : earth,
                state().startsWith("risk") ? VehicleSchedule.Context.SURFACE : VehicleSchedule.Context.ORBIT, null), List.of());
        var c = new VehicleSchedule.Visit(UUID.fromString("10000000-0000-0000-0000-000000000003"),
            new VehicleSchedule.Place(luna, VehicleSchedule.Context.ORBIT, null), List.of());
        VehicleSchedule schedule = new VehicleSchedule(state().equals("empty") ? List.of() : List.of(a,b,c), VehicleSchedule.Mode.RUN_ONCE);
        for (var leg : schedule.routes().keySet()) schedule = schedule.withRouteSettings(leg,
            new VehicleSchedule.RouteSettings(12000, RoutePriority.PRIORITIZE_DV));
        var bodies = CelestialRegistry.numericalDefinitions(200000);
        var catalog = new VehicleScheduleUI.Catalog(List.of(bodies.get(earth), bodies.get(luna)), List.of(
                new VehicleScheduleUI.EndpointChoice(earth, endpointContext, endpoint, endpointKind,
                    silo ? "Supply Silo" : orbitalEndpoint ? "Station Terminal" : "Launch Site", orbitalEndpoint ? station : null)),
            List.of(), List.of(new VehicleScheduleUI.StationChoice(station, earth, "Orbital Station")),
            0, new OrbitalMechanics.CostModel(1000, 100, .35, .05, 20, 20, 10),
            bodies.values().stream().filter(body -> body.parentKey() == null).findFirst().orElseThrow());
        var vehicle = UUID.fromString("40000000-0000-0000-0000-000000000001");
        var locked = state().equals("waiting") ? Set.of(a.id()) : Set.<UUID>of();
        boolean servicing = state().equals("waiting");
        boolean risk = state().startsWith("risk");
        var warning = risk ? new Rocket.LaunchWarning(new VehicleSchedule.Leg(a.id(), b.id()), a.place(),
            b.place(), 8700, 1400, !state().equals("risk-unknown")) : null;
        var modules = List.of(
            new Rocket.ModuleView(UUID.fromString("60000000-0000-0000-0000-000000000001"), "galaxia.rocket.module.basic_capsule", 95, Rocket.HolderKind.INSTALLED, false, false, null),
            new Rocket.ModuleView(UUID.fromString("60000000-0000-0000-0000-000000000002"), "galaxia.rocket.module.basic_engine", 28, servicing ? Rocket.HolderKind.ASSEMBLER : Rocket.HolderKind.INSTALLED, servicing, false, servicing ? "galaxia.rocket.schedule.work.repair_waiting" : null),
            new Rocket.ModuleView(UUID.fromString("60000000-0000-0000-0000-000000000003"), "galaxia.rocket.module.basic_fuel_tank", servicing ? 100 : 40, servicing ? Rocket.HolderKind.GANTRY : Rocket.HolderKind.INSTALLED, servicing, servicing, servicing ? "galaxia.rocket.schedule.work.return" : null));
        var view = new Rocket.ScheduleView(vehicle, 8, schedule, servicing ? Rocket.Status.DISASSEMBLED : Rocket.Status.PHYSICAL, a.place(),
            locked.isEmpty() ? null : a.id(), locked, state().equals("waiting") ? "galaxia.rocket.schedule.waiting_endpoint" : null, servicing ? null : new OrbitalMechanics.LocalVehicle(1400, 0, 3200, 800000), Map.of(), Map.of(), modules, modules.size(), servicing ? endpoint : null, warning, null);
        var session = new VehicleScheduleUI.Session();
        session.receive(view, catalog, null, false);
        session.commands(draft -> draft.receive(new Rocket.ScheduleView(vehicle, draft.baseRevision()+1,
            draft.draft(), view.status(), a.place(), view.activeVisitId(), view.lockedVisitIds(), view.problemKey(), view.propulsion(), view.propellant(), view.cargo(), view.modules(), view.requiredModules(), view.returnSiloId(), view.launchWarning(), view.manualMission()), catalog, null, true), null, (draft, proceed) -> {
                var current = draft.view();
                draft.receive(new Rocket.ScheduleView(vehicle, current.revision() + 1, current.schedule(),
                    current.status(), current.place(), current.activeVisitId(), current.lockedVisitIds(), null,
                    current.propulsion(), current.propellant(), current.cargo(), current.modules(),
                    current.requiredModules(), current.returnSiloId(), null, current.manualMission()), catalog, null, true);
            });
        if (state().startsWith("manual")) {
            var landing = new VehicleSchedule.Place(luna, VehicleSchedule.Context.SURFACE, null,
                new VehicleSchedule.SurfaceSite(7, 120, 70, -40, new Rocket.Footprint(3, 10, 3)));
            session.manualCommands(draft -> {
                if (!state().equals("manual-searching")) draft.receiveLandingSearch(vehicle, draft.view().revision(),
                    draft.landingQuery(), landing, null);
            }, draft -> {
                var from = new VehicleSchedule.Visit(a.id(), view.place(), List.of());
                var to = new VehicleSchedule.Visit(b.id(), draft.landingSite(), List.of());
                var mission = new VehicleSchedule(List.of(from, to), VehicleSchedule.Mode.RUN_ONCE)
                    .withRouteSettings(new VehicleSchedule.Leg(from.id(), to.id()), draft.manualRoute());
                draft.receive(manualView(draft.view(), mission), catalog, null, true);
            }, draft -> draft.receive(manualView(draft.view(), null), catalog, null, true));
            session.openManual();
            session.manualDestination(luna, "120", "-40");
            session.manualRoute(new VehicleSchedule.RouteSettings(12000, RoutePriority.PRIORITIZE_DV));
            session.requestLandingSearch();
            if (state().equals("manual-accepted")) session.requestLandingAcceptance();
        }
        if (state().startsWith("body-controls")) {
            var selected = state().equals("body-controls-known") ? luna : CelestialObjectKey.registered(CelestialObjectId.MARS);
            return new ModularPanel("galaxia:body_selection_controls").size(290, 116)
                .background(new Rectangle().color(EnumColors.MAP_COLOR_MODAL_BG.getColor()))
                .child(VehicleScheduleUI.bodySelectionControls(session, () -> selected,
                    () -> session.selectDestinationBody(selected), () -> {}).pos(10, 10));
        }
        if (state().equals("body-returned")) {
            session.selectVisit(c.id());
            session.selectDestinationBody(earth);
        }
        if (state().equals("many")) for (int i = 0; i < 6; i++) session.addVisit();
        if (state().equals("editing")) session.moveVisit(c.id(), -1);
        if (state().equals("invalid")) session.fail("galaxia.rocket.schedule.allowance_missing");
        if (state().equals("invalid-refuel")) session.replaceVisit(new VehicleSchedule.Visit(a.id(),
            new VehicleSchedule.Place(earth, VehicleSchedule.Context.SURFACE,
                new VehicleSchedule.Group("Landing Pads", Kind.LANDING_PAD, null)), a.actions()));
        return VehicleScheduleUI.build(session);
    }
    private static Rocket.ScheduleView manualView(Rocket.ScheduleView view, VehicleSchedule mission) {
        return new Rocket.ScheduleView(view.vehicleId(), view.revision() + 1, view.schedule(), view.status(),
            view.place(), view.activeVisitId(), view.lockedVisitIds(), view.problemKey(), view.propulsion(),
            view.propellant(), view.cargo(), view.modules(), view.requiredModules(), view.returnSiloId(), null, mission);
    }
    public static final class BodyReturned extends VehicleSchedulePreview { @Override protected String state() { return "body-returned"; } }
    public static final class BodyControlsKnown extends VehicleSchedulePreview { @Override protected String state() { return "body-controls-known"; } }
    public static final class BodyControlsUnknown extends VehicleSchedulePreview { @Override protected String state() { return "body-controls-unknown"; } }
    public static final class Many extends VehicleSchedulePreview { @Override protected String state() { return "many"; } }
    public static final class Manual extends VehicleSchedulePreview { @Override protected String state() { return "manual"; } }
    public static final class ManualSearching extends VehicleSchedulePreview { @Override protected String state() { return "manual-searching"; } }
    public static final class ManualAccepted extends VehicleSchedulePreview { @Override protected String state() { return "manual-accepted"; } }
    public static final class Empty extends VehicleSchedulePreview { @Override protected String state() { return "empty"; } }
    public static final class Waiting extends VehicleSchedulePreview { @Override protected String state() { return "waiting"; } }
    public static final class Editing extends VehicleSchedulePreview { @Override protected String state() { return "editing"; } }
    public static final class Invalid extends VehicleSchedulePreview { @Override protected String state() { return "invalid"; } }
    public static final class StationPad extends VehicleSchedulePreview { @Override protected String state() { return "station-pad"; } }
    public static final class Risk extends VehicleSchedulePreview { @Override protected String state() { return "risk"; } }
    public static final class RiskUnknown extends VehicleSchedulePreview { @Override protected String state() { return "risk-unknown"; } }
    public static final class Dock extends VehicleSchedulePreview { @Override protected String state() { return "dock"; } }
    public static final class SiloFull extends VehicleSchedulePreview { @Override protected String state() { return "silo-full"; } }
    public static final class SiloExact extends VehicleSchedulePreview { @Override protected String state() { return "silo-exact"; } }
    public static final class InvalidRefuel extends VehicleSchedulePreview { @Override protected String state() { return "invalid-refuel"; } }

    /** Explicit numerical view states exercise the production label without a world bootstrap. */
    public static class ForecastAvailable implements PreviewEntrypoint {
        @Override public String owner() { return "galaxia"; }
        @Override public Class<?> previewedClass() { return VehicleScheduleUI.class; }
        protected OrbitalTransferPlanner.ForecastEstimate estimate() {
            return new OrbitalTransferPlanner.ForecastEstimate(OrbitalTransferPlanner.ForecastStatus.AVAILABLE,
                OrbitalTransferPlanner.summarizeCircularAvailability(new boolean[] {true, false, false, true}, .01, 0));
        }
        @Override public Object createPanel(Context context) {
            return new ModularPanel("galaxia:forecast_preview").size(300, 65)
                .background(new Rectangle().color(EnumColors.MAP_COLOR_MODAL_BG.getColor()))
                .child(VehicleScheduleUI.createForecastGuidanceWidget(this::estimate));
        }
    }
    public static final class ForecastFixed extends ForecastAvailable {
        @Override protected OrbitalTransferPlanner.ForecastEstimate estimate() {
            return new OrbitalTransferPlanner.ForecastEstimate(OrbitalTransferPlanner.ForecastStatus.AVAILABLE,
                OrbitalTransferPlanner.summarizeCircularAvailability(new boolean[] {true}, 0, 0));
        }
    }
    public static final class ForecastNoWindow extends ForecastAvailable {
        @Override protected OrbitalTransferPlanner.ForecastEstimate estimate() {
            return new OrbitalTransferPlanner.ForecastEstimate(OrbitalTransferPlanner.ForecastStatus.AVAILABLE,
                OrbitalTransferPlanner.summarizeCircularAvailability(new boolean[] {false, false, false, false}, .01, 0));
        }
    }
    public static final class ForecastUnsupported extends ForecastAvailable {
        @Override protected OrbitalTransferPlanner.ForecastEstimate estimate() {
            return new OrbitalTransferPlanner.ForecastEstimate(OrbitalTransferPlanner.ForecastStatus.UNSUPPORTED_FAMILY, null);
        }
    }
    public static final class ForecastUnavailableData extends ForecastAvailable {
        @Override protected OrbitalTransferPlanner.ForecastEstimate estimate() {
            return new OrbitalTransferPlanner.ForecastEstimate(OrbitalTransferPlanner.ForecastStatus.UNAVAILABLE_LOCAL_DATA, null);
        }
    }
    public static final class ForecastStale extends ForecastAvailable {
        @Override protected OrbitalTransferPlanner.ForecastEstimate estimate() {
            return new OrbitalTransferPlanner.ForecastEstimate(OrbitalTransferPlanner.ForecastStatus.STALE, null);
        }
    }

    public static class Transit extends VehicleSchedulePreview {
        @Override public Class<?> previewedClass() { return GalacticMapWidget.class; }
        @Override public Object createPanel(Context context) {
            var bodies = CelestialRegistry.numericalDefinitions(200000);
            var earth = bodies.get(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD));
            var source = new VehicleSchedule.Place(earth.key(), VehicleSchedule.Context.SURFACE, null);
            var destination = new VehicleSchedule.Place(earth.key(), VehicleSchedule.Context.ORBIT, null);
            var program = state().equals("stop") ? null : localProgram(earth);
            long total = program == null ? 0 : program.obligations().stream().mapToLong(phase -> Math.max(1,
                (long) Math.ceil((phase.endEpochOsu() - phase.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_TICK))).sum();
            var view = state().equals("stop")
                ? new Rocket.TransitView(UUID.fromString("40000000-0000-0000-0000-000000000001"), destination,
                    destination, null, 0, 0, true, "galaxia.rocket.schedule.waiting_endpoint")
                : new Rocket.TransitView(UUID.fromString("40000000-0000-0000-0000-000000000001"), source,
                    destination, program, total / 2, total - total / 2, true, null);
            return new ModularPanel("galaxia:transit_preview").size(360, 76).child(
                GalacticMapWidget.createTransitStatusWidget(() -> view, key -> Optional.ofNullable(bodies.get(key))));
        }

        private static OrbitalTransferPlanner.PoweredCandidate localProgram(CelestialObject earth) {
            var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 800000);
            var model = new OrbitalMechanics.CostModel(1000, 100, .35, .05, 20, 20, 10);
            double dv = OrbitalMechanics.surfaceManeuverDeltaV(earth.properties(), model, false);
            var phase = OrbitalMechanics.approximateBurn(OrbitalMechanics.ManeuverKind.ASCENT, earth, vehicle,
                vehicle.wetMassKg(), dv, 0, model);
            double distance = OrbitalMechanics.distanceToMetres(earth.properties().parkingOrbitRadius())
                - earth.properties().radiusMetres();
            return new OrbitalTransferPlanner.PoweredCandidate(OrbitalTransferPlanner.POWERED_MODEL_ID, null,
                List.of(phase), vehicle.wetMassKg(), vehicle.wetMassKg() - phase.propellantKg(), .7,
                dv, phase.propellantKg(), phase.physicalSeconds(), distance, Map.of(), phase.status());
        }
    }
    public static final class TransitStop extends Transit { @Override protected String state() { return "stop"; } }
}
