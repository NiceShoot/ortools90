package com.example.ortools90.cp;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Cp护士排班 {

    public static void main(String[] args) {

        /**
         * 在下一个示例中，医院主管需要在三天内为四名护士创建一个时间表，但需满足以下条件：
         *
         * 每天分为三个 8 小时轮班。
         * 每天，每个班次都分配给一名护士，没有护士工作超过一个班次。
         * 在三天的时间里，每位护士至少被分配到两个班次。
         */


        Loader.loadNativeLibraries();

        // 基础数据
        int nurseNum = 4;
        int daysNum = 3;
        int shiftsNum = 3;
        int[] nurses = IntStream.range(0, nurseNum).toArray();
        int[] days = IntStream.range(0, daysNum).toArray();
        int[] shifts = IntStream.range(0, shiftsNum).toArray();

        // 组建数据
        CpModel model = new CpModel();
        IntVar[][][] linearArguments = new IntVar[nurseNum][daysNum][shiftsNum];
        for (int nurs : nurses) {
            for (int day : days) {
                for (int shift : shifts) {
                    linearArguments[nurs][day][shift] = model.newBoolVar("shifts_护士：" + nurs + "，第几天：" + day + "，第几班：" + shift);
                }
            }
        }
        // 创建约束--每天 每个班次分给一名护士
        for (int day : days) {
            for (int shift : shifts) {
                Constraint constraint = new Constraint(model.getBuilder());
                BoolArgumentProto.Builder exactlyOneBuilder = constraint.getBuilder().getExactlyOneBuilder();
                for (int nurs : nurses) {
                    exactlyOneBuilder.addLiterals(linearArguments[nurs][day][shift].getIndex());
                }
            }
        }

        // 创建约束--每天 没有护士工作超过一个班次
        for (int nurs : nurses) {
            for (int day : days) {
                Constraint constraint = new Constraint(model.getBuilder());
                BoolArgumentProto.Builder atMostOneBuilder = constraint.getBuilder().getAtMostOneBuilder();
                for (int shift : shifts) {
                    atMostOneBuilder.addLiterals(linearArguments[nurs][day][shift].getIndex());
                }
            }
        }

        // 创建约束--在三天的时间里，每位护士至少被分配到两个班次
        int allShifts = daysNum * shiftsNum ;
        int minShiftsPerNurse = allShifts / nurseNum;
        int maxShiftsPerNurse = allShifts % nurseNum == 0?minShiftsPerNurse:minShiftsPerNurse+1;
        for (int nurs : nurses) {
            List<IntVar> l = new ArrayList<>(daysNum*shiftsNum);
            for (int day : days) {
                for (int shift : shifts) {
                    l.add(linearArguments[nurs][day][shift]);
                }
            }
            model.addLinearConstraint(LinearExpr.sum(l.toArray(new IntVar[daysNum * shiftsNum])),minShiftsPerNurse,maxShiftsPerNurse);
        }


        // 求解
        CpSolver solver = new CpSolver();
        solver.getParameters().setLinearizationLevel(0);
        solver.getParameters().setEnumerateAllSolutions(true);
        // Display the first five solutions.
        final int solutionLimit = 5;

        VarArraySolutionPrinterWithLimit cb =
                new VarArraySolutionPrinterWithLimit(nurses, days, shifts, linearArguments, solutionLimit);

        // Creates a solver and solves the model.
        CpSolverStatus status = solver.solve(model, cb);
        System.out.println("Status: " + status);
        System.out.println(cb.getSolutionCount() + " solutions found.");

        // Statistics.
        System.out.println("Statistics");
        System.out.printf("  conflicts: %d%n", solver.numConflicts());
        System.out.printf("  branches : %d%n", solver.numBranches());
        System.out.printf("  wall time: %f s%n", solver.wallTime());
    }





    // 回调
    public static class VarArraySolutionPrinterWithLimit extends CpSolverSolutionCallback {

        private int solutionCount;
        private final int[] allNurses;
        private final int[] allDays;
        private final int[] allShifts;
        private final Literal[][][] shifts;
        private final int solutionLimit;

        public VarArraySolutionPrinterWithLimit(
                int[] allNurses, int[] allDays, int[] allShifts, Literal[][][] shifts, int limit) {
            solutionCount = 0;
            this.allNurses = allNurses;
            this.allDays = allDays;
            this.allShifts = allShifts;
            this.shifts = shifts;
            solutionLimit = limit;
        }

        @Override
        public void onSolutionCallback() {
            System.out.printf("Solution #%d:%n", solutionCount);
            for (int d : allDays) {
                System.out.printf("Day %d%n", d);
                for (int n : allNurses) {
                    boolean isWorking = false;
                    for (int s : allShifts) {
                        if (booleanValue(shifts[n][d][s])) {
                            isWorking = true;
                            System.out.printf("  Nurse %d work shift %d%n", n, s);
                        }
                    }
                    if (!isWorking) {
                        System.out.printf("  Nurse %d does not work%n", n);
                    }
                }
            }
            solutionCount++;
            if (solutionCount >= solutionLimit) {
                System.out.printf("Stop search after %d solutions%n", solutionLimit);
                stopSearch();
            }
        }

        public int getSolutionCount() {
            return solutionCount;
        }
    }
}
