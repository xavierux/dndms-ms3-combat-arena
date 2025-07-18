package com.xvclemente.dnd.ms3.controller;

import com.xvclemente.dnd.ms3.service.CombatSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/combat-logs")
public class CombatLogController {

    private final CombatSimulationService combatSimulationService;

    public CombatLogController(CombatSimulationService combatSimulationService) {
        this.combatSimulationService = combatSimulationService;
    }

    @GetMapping("/{adventureId}")
    public ResponseEntity<List<String>> getCombatLog(@PathVariable String adventureId) {
        List<String> log = combatSimulationService.getCombatLogForAdventure(adventureId);
        return ResponseEntity.ok(log);
    }
}