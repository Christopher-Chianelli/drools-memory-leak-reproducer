package memory.leak.reproducer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import memory.leak.reproducer.domain.roster.Roster;
import memory.leak.reproducer.generator.RosterGenerator;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.impl.heuristic.selector.move.generic.PillarChangeMove;

public class Main {
    public static void main(String[] args) {
	RosterGenerator rosterGenerator = new RosterGenerator();
	Roster roster = rosterGenerator.generateRoster(100, 28 * 4, 100);

    SolverFactory<Roster> factory = SolverFactory
        .createFromXmlResource("memory/leak/reproducer/service/solver/employeeRosteringSolverConfig.xml");
    Solver<Roster> solver = factory.buildSolver();
    
    solver.addMoveListener((m) -> {
        if (m instanceof PillarChangeMove) {
            PillarChangeMove<Roster> move = (PillarChangeMove<Roster>) m;
            if (move.getPillar().size() == 10793) {
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("MOVE_LIST.txt"),
                                                                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    writer.write(move.toString());
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    });
	Roster solution = solver.solve(roster);

	System.out.printf("The best score is: %s.\n", solution.getScore().toString());
    }
}
