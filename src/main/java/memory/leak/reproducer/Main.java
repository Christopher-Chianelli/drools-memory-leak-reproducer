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
import org.kie.internal.event.rule.RuleEventManager;
import org.optaplanner.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import org.optaplanner.core.api.score.holder.ScoreHolder;
import org.optaplanner.core.impl.score.buildin.hardmediumsoftlong.HardMediumSoftLongScoreDefinition;
import org.optaplanner.core.impl.score.director.drools.OptaPlannerRuleEventListener;

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
    ((RuleEventManager) kieSession).addEventListener(new OptaPlannerRuleEventListener());

    kieSession.setGlobal("scoreHolder", scoreHolder);
    
    roster.getShiftList().forEach(shift -> kieSession.insert(shift));
    roster.getEmployeeAvailabilityList().forEach(ea -> kieSession.insert(ea));
    roster.getEmployeeList().forEach(e -> kieSession.insert(e));
    roster.getSkillList().forEach(s -> kieSession.insert(s));
    roster.getSpotList().forEach(s -> kieSession.insert(s));
    
    
    try (BufferedReader reader = Files.newBufferedReader(Paths.get("MOVE_LIST.txt"))) {
        String line;
        DataType readType = null;
        DataHandler dataHandler = null;
        
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            final String READ_LINE = line;
            if (readType == null) {
                readType = Arrays.asList(DataType.values()).stream().filter(type -> type.isStart(READ_LINE)).findAny().orElseThrow(IllegalStateException::new);
                dataHandler = readType.getDataHandler();
                dataHandler.init(roster, shiftIdToShiftMap, employeeIdToEmployeeMap);
            }
            else {
                if (readType.isEnd(READ_LINE)) {
                    List<Pair<Shift, Employee>> move = dataHandler.getMove();
                    List<Pair<Shift, Employee>> undoMove = dataHandler.getUndoMove();
                    // Do the drools thing
                    move.forEach(pair -> {
                        pair.getLeft().setEmployee(pair.getRight());
                        kieSession.update(kieSession.getFactHandle(pair.getLeft()), pair.getLeft(), "employee");
                    });
                    if (!undoMove.isEmpty()) {
                        kieSession.fireAllRules();
                        undoMove.forEach(pair -> {
                            pair.getLeft().setEmployee(pair.getRight());
                            kieSession.update(kieSession.getFactHandle(pair.getLeft()), pair.getLeft(), "employee");
                        });
                    }
                    readType = null;
                }
                else {
                    dataHandler.handleLine(READ_LINE);
                }

            }
        }
	}
    catch(IOException e) {
        e.printStackTrace();
    }

    }
    
    enum DataType {
        MOVE("MOVE", MoveDataHandler::new),
        SOLUTION("SOLUTION", SolutionDataHandler::new);
        
        String id;
        Supplier<DataHandler> getDataHandler;
        
        DataType(String id, Supplier<DataHandler> getDataHandler) {
            this.id = id;
            this.getDataHandler = getDataHandler;
        }
        
        public DataHandler getDataHandler() {
            return getDataHandler.get();
        }
        
        public boolean isStart(String line) {
            return line.equals("<" + id + ">");
        }
        
        public boolean isEnd(String line) {
            return line.equals("</" + id + ">");
        }
    }
    
    interface DataHandler {
        void init(Roster roster, Map<Long, Shift> shiftIdToShiftMap, Map<Long, Employee> employeeIdToEmployeeMap);
        void handleLine(String line);
        List<Pair<Shift, Employee>> getMove();
        List<Pair<Shift, Employee>> getUndoMove();
    }
    
    static class MoveDataHandler implements DataHandler {
        Employee moveTo;
        List<Shift> affectedShifts;
        Roster originalRoster;
        boolean firstLine;
        
        Map<Long, Shift> shiftIdToShiftMap;
        Map<Long, Employee> employeeIdToEmployeeMap;
        
        @Override
        public void init(Roster roster, Map<Long, Shift> shiftIdToShiftMap, Map<Long, Employee> employeeIdToEmployeeMap) {
            this.originalRoster = roster;
            this.shiftIdToShiftMap = shiftIdToShiftMap;
            this.employeeIdToEmployeeMap = employeeIdToEmployeeMap;
            affectedShifts = new ArrayList<>();
            firstLine = true;
        }
        
        @Override
        public void handleLine(String line) {
            if (firstLine) {
                moveTo = (line.equals("null")? null : employeeIdToEmployeeMap.get(Long.valueOf(line)));
                firstLine = false;
            }
            else {
                affectedShifts.add(shiftIdToShiftMap.get(Long.valueOf(line)));
            }
        }
        
        @Override
        public List<Pair<Shift, Employee>> getMove() {
            return affectedShifts.stream().map(s -> Pair.of(s, moveTo)).collect(Collectors.toList());
        }
        
        @Override
        public List<Pair<Shift, Employee>> getUndoMove() {
            return affectedShifts.stream().map(s -> Pair.of(s, s.getEmployee())).collect(Collectors.toList());
        }
        
    }
    
    static class SolutionDataHandler implements DataHandler {
        Roster roster;
        
        List<Shift> shiftList;
        int shiftIndex;
        Map<Long, Employee> employeeIdToEmployeeMap;
        Map<Long, Employee> shiftIdToEmployeeMap;
        
        @Override
        public void init(Roster roster, Map<Long, Shift> shiftIdToShiftMap, Map<Long, Employee> employeeIdToEmployeeMap) {
            this.roster = roster;
            this.employeeIdToEmployeeMap = employeeIdToEmployeeMap;
            shiftIdToEmployeeMap = new HashMap<>();
            shiftIndex = 0;
            shiftList = new ArrayList<>(roster.getShiftList().size());
        }
        
        @Override
        public void handleLine(String line) {
            Employee employee = (line.equals("null"))? null : employeeIdToEmployeeMap.get(Long.valueOf(line));
            Shift shift = roster.getShiftList().get(shiftIndex);
            shiftIdToEmployeeMap.put(shift.getId(), employee);
            shiftList.add(shift);
            shiftIndex++;
        }
        
        @Override
        public List<Pair<Shift, Employee>> getMove() {
            return shiftList.stream().map(s -> Pair.of(s, shiftIdToEmployeeMap.get(s.getId()))).collect(Collectors.toList());
        }
        
        public List<Pair<Shift, Employee>> getUndoMove() {
            return Collections.emptyList();
        }
        
    }
}
