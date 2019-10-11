package org.optaweb.employeerostering;

import org.optaweb.employeerostering.domain.roster.Roster;
import org.optaweb.employeerostering.generator.RosterGenerator;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;

public class Main {
    public static void main(String[] args) {
	RosterGenerator rosterGenerator = new RosterGenerator();
	Roster roster = rosterGenerator.generateRoster(100, 28 * 4, 100);

        SolverFactory<Roster> factory = SolverFactory
          .createFromXmlResource("org/optaweb/employeerostering/service/solver/employeeRosteringSolverConfig.xml");
        Solver<Roster> solver = factory.buildSolver();
	Roster solution = solver.solve(roster);

	System.out.printf("The best score is: %s.\n", solution.getScore().toString());
    }
}
