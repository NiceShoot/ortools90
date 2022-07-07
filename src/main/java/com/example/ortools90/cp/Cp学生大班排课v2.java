package com.example.ortools90.cp;

import com.google.common.collect.Lists;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Cp学生大班排课v2 {

    public static void main(String[] args) {

        /**
         * 模拟学生排课
         *      时段：7天（周一到周日，周六日不上课），每天4个时段 共28个时段
         *      学生：1个
         *      频次：语文5，数学3，英语5，体育2，自习5
         *      每个时段只能上一科
         *      每科每天只能上一次
         *      自习课都在每天的最后一节课
         *      尽量均匀分布
         */
        Loader.loadNativeLibraries();
        int dayNum=7,timeNum=4,studentNum=1,subjectNum = 5;
        // 基础数据
        int[] days = IntStream.range(0, dayNum).toArray();
        int[] times = IntStream.range(0, timeNum).toArray();
        int[] students = IntStream.range(0, studentNum).toArray();
        int[] subjects = IntStream.range(0, subjectNum).toArray();
        // 周课频
        Map<Integer,Integer> frequency = new HashMap<>();
        frequency.put(0,5);frequency.put(1,3);frequency.put(2,5);frequency.put(3,2);frequency.put(4,5);
        // 科目名称
        Map<Integer,String> subNameMap = new HashMap<>();
        subNameMap.put(0,"语文");subNameMap.put(1,"数学");subNameMap.put(2,"英语");subNameMap.put(3,"体育");subNameMap.put(4,"自习");
        // 周次名称
        Map<Integer,String> weekNameMap = new HashMap<>();
        weekNameMap.put(0,"周一");weekNameMap.put(1,"周二");weekNameMap.put(2,"周三");weekNameMap.put(3,"周四");weekNameMap.put(4,"周五");weekNameMap.put(5,"周六");weekNameMap.put(6,"周日");
        // 国家规定周六日不能上课,[day][time]
        int[][] countryAvailableTime  = new int[][]{
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {0,0,0,0},
                {0,0,0,0}
        };

        // 组建模型
        CpModel model = new CpModel();
        // 所有点位
        IntVar[][][][] lessonPoints = new IntVar[studentNum][subjectNum][dayNum][timeNum];
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    for (Integer time : times){
                        lessonPoints[stu][sub][day][time] = model.newBoolVar(stu+","+sub+","+day+","+time);
                    }
                }
            }
        }

        // 学生可排时段
        List<IntVar> l = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    for (Integer time : times){
                        l.add(lessonPoints[stu][sub][day][time]);
                        if (countryAvailableTime[day][time]  == 1){
                            c.add(1);
                        }else {
                            c.add(-1);
                        }
                    }
                }
            }
        }
        LinearExpr linearExpr = LinearExpr.scalProd(l.toArray(new IntVar[studentNum * subjectNum * dayNum * timeNum]), c.stream().mapToInt(Integer::intValue).toArray());
        model.maximize(linearExpr);

        // 尽量均匀，尽量均匀散列
        for (Integer stu : students){
            for (Integer sub : subjects){
                Integer fre = frequency.get(sub);
                int jumpNum ;
                if (fre >1 ){
                    jumpNum = dayNum / fre;
                    int[] t = new int[]{0,1,2,3,4};
                    List<Integer> dayList = Arrays.stream(t).boxed().collect(Collectors.toList());
                    List<List<Integer>> partition = Lists.partition(dayList, jumpNum);
                    partition.forEach(dayPage -> {
                        List<IntVar> list = new ArrayList<>();
                        dayPage.forEach(day -> {
                            for (Integer time : times){
                                list.add(lessonPoints[stu][sub][day][time]);
                            }
                        });
                        model.addBoolOr(list.toArray(new IntVar[dayPage.size()*timeNum]));
                    });
                }
            }
        }


        // 每个时段只能上一科
        for (Integer stu : students){
            for (Integer day : days){
                for (Integer time : times){
                    List<IntVar> list = new ArrayList<>();
                    for (Integer sub : subjects){
                        list.add(lessonPoints[stu][sub][day][time]);
                    }

                    model.addLessOrEqual(LinearExpr.sum(list.toArray(new IntVar[subjectNum])),1);
                }
            }
        }

        // 每个科目每天最多只能上一次
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    List<IntVar> list = new ArrayList<>();
                    for (Integer time : times){
                        list.add(lessonPoints[stu][sub][day][time]);
                    }
                    model.addLessOrEqual(LinearExpr.sum(list.toArray(new IntVar[timeNum])),1);
                }
            }
        }

        // 设置每一科的课频
        for (Integer stu : students){
            for (Integer sub : subjects){
                List<IntVar> list = new ArrayList<>();
                for (Integer day : days){
                    for (Integer time : times){
                        list.add(lessonPoints[stu][sub][day][time]);
                    }
                }
                model.addEquality(LinearExpr.sum(list.toArray(new IntVar[dayNum*timeNum])),frequency.get(sub));
            }
        }

        // 尽量上下午的最后一节课都是自习课
        for (Integer stu : students){
            for (Integer sub : subjects){
                List<IntVar> list = new ArrayList<>();
                List<Integer> listcc = new ArrayList<>();
                if (sub == 4){
                    for (Integer day : days){
                        for (int i=0;i<timeNum;i++){
                            list.add(lessonPoints[stu][sub][day][i]);
                            if (i==3){
                                listcc.add(1);
                            }else {
                                listcc.add(-1);
                            }
                        }
                    }
                    LinearExpr linearExpr1 = LinearExpr.scalProd(list.toArray(new IntVar[list.size()]), listcc.stream().mapToInt(Integer::intValue).toArray());
                    //model.addEquality(builder,frequency.get(sub));
                    model.maximize(linearExpr1);
                }
            }
        }

        // 体育课尽量是di三节课
        for (Integer stu : students){
            for (Integer sub : subjects){
                List<IntVar> list = new ArrayList<>();
                List<Integer> listcc = new ArrayList<>();
                if (sub == 3){
                    for (Integer day : days){
                        for (int i=0;i<timeNum;i++){
                            list.add(lessonPoints[stu][sub][day][i]);
                            if (i==0 || i==1  || i==3 ){
                                listcc.add(-1);
                            }else {
                                listcc.add(1);
                            }
                        }
                    }
                    LinearExpr linearExpr1 = LinearExpr.scalProd(list.toArray(new IntVar[list.size()]), listcc.stream().mapToInt(Integer::intValue).toArray());
                    //model.addEquality(linearExpr1,frequency.get(sub));
                    model.maximize(linearExpr1);
                }
            }
        }



        // 求解
        CpSolver solver = new CpSolver();
        solver.getParameters().setLinearizationLevel(1);
        CpSolverStatus status = solver.solve(model);
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.printf("Solution:%n");
            for (int stu : students){
                System.out.printf("学生 %d%n",stu);

                StringBuilder dayBuilder = new StringBuilder("   ");
                for (int day : days) {
                    dayBuilder.append(weekNameMap.get(day)).append("  ");
                }
                System.out.println(dayBuilder.toString());
                for (int i = 0 ;i<times.length;i++) {
                    StringBuilder timeBuilder = new StringBuilder(i+"  ");
                    for (int day : days) {
                        for (int sub : subjects) {
                            if (solver.booleanValue(lessonPoints[stu][sub][day][i])) {
                                timeBuilder.append(subNameMap.get(sub)).append("  ");
                            }
                        }
                    }
                    System.out.println(timeBuilder.toString());
                }
            }

        } else {
            System.out.printf("No optimal solution found !");
        }
        // Statistics.
        System.out.println("Statistics");
        System.out.printf("  conflicts: %d%n", solver.numConflicts());
        System.out.printf("  branches : %d%n", solver.numBranches());
        System.out.printf("  wall time: %f s%n", solver.wallTime());
    }

}
