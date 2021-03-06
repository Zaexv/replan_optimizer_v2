package logic;

import entities.*;
import entities.parameters.EvaluationParameters;
import io.swagger.model.ApiPlanningSolution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kredes on 22/06/2017.
 */
public class SolutionEvaluator {

    private static SolutionEvaluator instance = new SolutionEvaluator();

    public static SolutionEvaluator getInstance() {
        return instance;
    }

    private SolutionEvaluator() {}


    /* --- OBJECTIVES --- */
    public double completionObjective(PlanningSolution solution) {
    	if (solution.getProblem().getFeatures().size() == 0) return 0.0;
        return (double) solution.getUndoneFeatures().size() / (double) (solution.getProblem().getFeatures().size());
    }

    public double endDateObjective(PlanningSolution solution) {
        if (solution.getPlannedFeatures().size() == 0) return 0.0;

        Schedule schedule = solution.getSchedule();
        DaySlot planEndDaySlot = schedule.getEndDaySlot(SlotStatus.Used);
        DaySlot worstEndDaySlot = schedule.getEndDaySlot(null);
        return Math.min(0.0,  planEndDaySlot.getTime() /  worstEndDaySlot.getTime());
    }
    
    public double distributionObjective(PlanningSolution solution) {
    	if (solution.getPlannedFeatures().size() == 0) return 0.0;
    	Map<Employee, Double> hoursPerEmployee = new HashMap<>();
        double avg = 0.0;

        for (Employee e : solution.getSchedule().getEmployeesCalendar().keySet()) {
            double ratio = solution.getSchedule().getRatio(e);
            hoursPerEmployee.put(e, solution.getSchedule().getRatio(e));
            avg += ratio;
        }
        
        double totalEmployees = solution.getProblem().getEmployees().size();
        //Calculates the standard deviation of the hours
        double expectedAvg = avg/totalEmployees;
        double sum = 0.0;
        for (Double nbHours : hoursPerEmployee.values()) {
            sum += Math.pow(nbHours - expectedAvg, 2);
        }
        double standardDeviation = Math.sqrt(sum/totalEmployees);
        
        //Normalizes the standard deviation
        double max = Math.sqrt(((totalEmployees - 1.0) * Math.pow(expectedAvg, 2) + Math.pow(avg - expectedAvg, 2))/totalEmployees);
        double normalizedSd = max > 0 ? standardDeviation / max : 0;
        		
        return normalizedSd;
    }
    
    public double priorityObjective(PlanningSolution solution) {
        double score = worstScore(solution.getProblem());
        if (score == 0.0) return 0.0;
    	return 1.0 - (score - solution.getPriorityScore()) / worstScore(solution.getProblem());
    }
    
    public double similarityObjective(PlanningSolution solution) {
        if (solution.getPlannedFeatures().size() == 0) return 0.0;
        Schedule previousSolution = solution.getProblem().getPreviousSchedule();
        double score = 0.0;
        for (int i = 0; i < solution.getPlannedFeatures().size(); ++i) {
            PlannedFeature pf = solution.getPlannedFeature(i);
            Employee e = pf.getEmployee();
            Feature f = pf.getFeature();
            //Checks if feature is done by the same employee and computes a normalized score according
            //to schedule variation
            PlannedFeature ppf = previousSolution.findJobOf(f);
            if (ppf != null && e.equals(ppf.getEmployee())) {
                DaySlot maxEnd = solution.getSchedule().getEndDaySlot(null);
                double maxDiff = Math.max(ppf.getBeginHour(), maxEnd.getEndHourAbsolute() - ppf.getBeginHour());
                double realDiff = Math.abs(ppf.getBeginHour() - pf.getBeginHour());
                score += 1.0 - realDiff / maxDiff;
            }
        }
        return 1.0 - score / solution.getPlannedFeatures().size();
    }
    
    private double objectivePriorityRange = 0.999;

    public double getObjectivePerPriorityLevel(PlanningSolution solution, int i) {
        EvaluationParameters evaluationParameters = solution.getProblem().getEvaluationParameters();
        HashMap<Integer, Double> objectives = evaluationParameters.getObjectivesOfPriority(i);
        double score = 0.0;
        for (Integer objectiveIndex : objectives.keySet()) {
            switch(objectiveIndex) {
                case EvaluationParameters.completionQuality:
                    score += completionObjective(solution) * objectives.get(objectiveIndex);
                    break;
                case EvaluationParameters.distributionQuality:
                    score += distributionObjective(solution) * objectives.get(objectiveIndex);
                    break;
                case EvaluationParameters.endDateQuality:
                    score += endDateObjective(solution) * objectives.get(objectiveIndex);
                    break;
                case EvaluationParameters.priorityQuality:
                    score += priorityObjective(solution) * objectives.get(objectiveIndex);
                    break;
                case EvaluationParameters.similarityQuality:
                    score += similarityObjective(solution) * objectives.get(objectiveIndex);
                default:
                    break;
            }
        }
        double max = objectivePriorityRange ;
        return score * max;
    }

    /* --- NEW QUALITY --- */
    public double newQuality(PlanningSolution solution) {

        EvaluationParameters evaluationParameters = solution.getProblem().getEvaluationParameters();
        int priorityLevels = evaluationParameters.getPriorityLevels();

        double quality = 0.0;

        for (int i = 0; i < priorityLevels; ++i) {
            quality += getObjectivePerPriorityLevel(solution, i) / Math.pow(10, i*3);
        }

       /* System.out.println("****");
        System.out.println(endDateObjective(solution));
        System.out.println(priorityObjective(solution));
        System.out.println(completionObjective(solution));
        System.out.println(distributionObjective(solution));
        System.out.println(similarityObjective(solution));
        System.out.println("****");*/


        return quality;

    }

	/* --- PRIVATE AUX --- */
    private double worstScore(NextReleaseProblem problem) {
        return problem.getFeatures().stream()
                .map(Feature::getPriority)
                .reduce(0.0, (sum, next) -> sum += next.getScore(), Double::sum);
    }
}