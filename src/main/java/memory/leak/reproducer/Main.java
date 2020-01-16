package memory.leak.reproducer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import memory.leak.reproducer.domain.employee.Employee;
import memory.leak.reproducer.domain.roster.Roster;
import memory.leak.reproducer.domain.shift.Shift;
import memory.leak.reproducer.generator.RosterGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.optaplanner.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import org.optaplanner.core.api.score.holder.ScoreHolder;
import org.optaplanner.core.impl.score.buildin.hardmediumsoftlong.HardMediumSoftLongScoreDefinition;

public class Main {
    public static void main(String[] args) {
        RosterGenerator rosterGenerator = new RosterGenerator();
	    Roster roster = rosterGenerator.generateRoster(100, 28 * 4, 100);
	
	    Map<Long, Shift> shiftIdToShiftMap = new HashMap<>();
	    Map<Long, Employee> employeeIdToEmployeeMap = new HashMap<>();
	
	    roster.getShiftList().forEach(shift -> shiftIdToShiftMap.put(shift.getId(), shift));
	    roster.getEmployeeList().forEach(employee -> employeeIdToEmployeeMap.put(employee.getId(), employee));

        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.write(kieServices.getResources()
                             .newFileSystemResource(new File("src/main/resources/memory/leak/reproducer/service/solver/employeeRosteringScoreRules.drl"), "UTF-8"));
        kieServices.newKieBuilder(kfs).buildAll();
        KieContainer kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
        KieSession kieSession = kieContainer.newKieSession();

        ScoreHolder<HardMediumSoftLongScore> scoreHolder = new HardMediumSoftLongScoreDefinition().buildScoreHolder(true);
        kieSession.setGlobal("scoreHolder", scoreHolder);
    
        roster.getShiftList().forEach(shift -> kieSession.insert(shift));
        roster.getEmployeeAvailabilityList().forEach(ea -> kieSession.insert(ea));
        roster.getEmployeeList().forEach(e -> kieSession.insert(e));
        roster.getSkillList().forEach(s -> kieSession.insert(s));
        roster.getSpotList().forEach(s -> kieSession.insert(s));
        
        final long SHIFT_ID = 9471L;
        final long EMPLOYEE_ID = 137L;
        // FIRST MOVE
        List<Long> affectedShifts = Arrays.asList(SHIFT_ID);
        Employee oldEmployee = shiftIdToShiftMap.get(affectedShifts.get(0)).getEmployee();
        Employee newEmployee = employeeIdToEmployeeMap.get(EMPLOYEE_ID);
    
        for (Long shiftId : affectedShifts) {
            Shift shift = shiftIdToShiftMap.get(shiftId);
            shift.setEmployee(newEmployee);
            kieSession.update(kieSession.getFactHandle(shift), shift, "employee");
        }
        kieSession.fireAllRules();
        for (Long shiftId : affectedShifts) {
            Shift shift = shiftIdToShiftMap.get(shiftId);
            shift.setEmployee(oldEmployee);
            kieSession.update(kieSession.getFactHandle(shift), shift, "employee");
        }
    
        // SECOND MOVE
        affectedShifts = Arrays.asList(SHIFT_ID);
        oldEmployee = shiftIdToShiftMap.get(affectedShifts.get(0)).getEmployee();
        newEmployee = employeeIdToEmployeeMap.get(EMPLOYEE_ID);
    
        for (Long shiftId : affectedShifts) {
            Shift shift = shiftIdToShiftMap.get(shiftId);
            shift.setEmployee(newEmployee);
            kieSession.update(kieSession.getFactHandle(shift), shift, "employee");
        }
        kieSession.fireAllRules();
        for (Long shiftId : affectedShifts) {
            Shift shift = shiftIdToShiftMap.get(shiftId);
            shift.setEmployee(oldEmployee);
            kieSession.update(kieSession.getFactHandle(shift), shift, "employee");
        }
    }
    
}
