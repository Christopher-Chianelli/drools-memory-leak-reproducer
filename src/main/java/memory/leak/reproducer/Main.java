package memory.leak.reproducer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

public class Main {
    public static void main(String[] args) {
	RosterGenerator rosterGenerator = new RosterGenerator();
	Roster roster = rosterGenerator.generateRoster(100, 28 * 4, 100);
	
	Map<Long, Shift> shiftIdToShiftMap = new HashMap<>();
	Map<Long, Employee> employeeIdToEmployeeMap = new HashMap<>();
	
	roster.getShiftList().forEach(shift -> shiftIdToShiftMap.put(shift.getId(), shift));
	roster.getEmployeeList().forEach(employee -> employeeIdToEmployeeMap.put(employee.getId(), employee));
    
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
                    Roster move = dataHandler.getMove();
                    // Do the drools thing
                    roster = dataHandler.getNewBase();
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
        Roster getMove();
        Roster getNewBase();
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
            }
            else {
                affectedShifts.add(shiftIdToShiftMap.get(Long.valueOf(line)));
            }
        }
        
        @Override
        public Roster getMove() {
            Set<Shift> affectedShiftSet = new HashSet<>(affectedShifts);
            List<Shift> newShiftList = new ArrayList<>(originalRoster.getShiftList().stream().filter(s -> !affectedShiftSet.contains(s)).collect(Collectors.toList()));
            affectedShifts.forEach(shift -> {
                Shift newShift = new Shift(shift.getTenantId(), shift.getSpot(), shift.getStartDateTime(), shift.getEndDateTime(),
                                           shift.getRotationEmployee());
                newShift.setId(shift.getId());
                newShift.setEmployee(moveTo);
                newShiftList.add(newShift);
            });
            return new Roster(originalRoster.getId(), originalRoster.getTenantId(), originalRoster.getSkillList(), originalRoster.getSpotList(), originalRoster.getEmployeeList(),
                                       originalRoster.getEmployeeAvailabilityList(), originalRoster.getRosterParametrization(),
                                       originalRoster.getRosterState(), newShiftList);
        }
        
        @Override
        public Roster getNewBase() {
            return originalRoster;
        }   
    }
    
    static class SolutionDataHandler implements DataHandler {
        Roster roster;
        
        List<Shift> shiftList;
        int shiftIndex;
        Map<Long, Employee> employeeIdToEmployeeMap;
        
        @Override
        public void init(Roster roster, Map<Long, Shift> shiftIdToShiftMap, Map<Long, Employee> employeeIdToEmployeeMap) {
            this.roster = roster;
            this.employeeIdToEmployeeMap = employeeIdToEmployeeMap;
            shiftIndex = 0;
            shiftList = new ArrayList<>(roster.getShiftList().size());
        }
        
        @Override
        public void handleLine(String line) {
            Employee employee = (line.equals("null"))? null : employeeIdToEmployeeMap.get(Long.valueOf(line));
            Shift shift = roster.getShiftList().get(shiftIndex);
            shift.setEmployee(employee);
            shiftList.add(shift);
        }
        
        @Override
        public Roster getMove() {
            roster.setShiftList(shiftList);
            return roster;
        }
        
        @Override
        public Roster getNewBase() {
            roster.setShiftList(shiftList);
            return roster;
        }
    }
}
