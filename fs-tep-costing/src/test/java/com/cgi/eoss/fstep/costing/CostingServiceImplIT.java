package com.cgi.eoss.fstep.costing;

import com.cgi.eoss.fstep.model.CostingExpression;
import com.cgi.eoss.fstep.model.DataSource;
import com.cgi.eoss.fstep.model.FstepFile;
import com.cgi.eoss.fstep.model.FstepService;
import com.cgi.eoss.fstep.model.Job;
import com.cgi.eoss.fstep.model.JobConfig;
import com.cgi.eoss.fstep.model.User;
import com.cgi.eoss.fstep.model.WalletTransaction;
import com.cgi.eoss.fstep.persistence.service.CostingExpressionDataService;
import com.cgi.eoss.fstep.persistence.service.UserDataService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {CostingConfig.class})
@TestPropertySource("classpath:test-costing.properties")
@Transactional
public class CostingServiceImplIT {

    @Autowired
    private CostingService costingService;

    @Autowired
    private CostingExpressionDataService costingExpressionDataService;

    @Autowired
    private UserDataService userDataService;

    @Test
    public void estimateJobCost() throws Exception {
        FstepService service = new FstepService("fstepService", null, "dockerTag");
        service.setId(1L);
        JobConfig jobConfig = new JobConfig();
        jobConfig.setId(1L);
        jobConfig.setService(service);

        int defaultCost = costingService.estimateJobCost(jobConfig);
        assertThat(defaultCost, is(1));

        CostingExpression costingExpression = CostingExpression.builder()
                .type(CostingExpression.Type.SERVICE)
                .associatedId(1L)
                .costExpression("1")
                .estimatedCostExpression("service.name.length()")
                .build();
        costingExpressionDataService.save(costingExpression);

        int cost = costingService.estimateJobCost(jobConfig);
        assertThat(cost, is(service.getName().length()));
    }

    @Test
    public void estimateDownloadCost() throws Exception {
        DataSource dataSource = new DataSource();
        dataSource.setId(1L);
        dataSource.setName("FS-TEP DataSource");
        FstepFile fstepFile = new FstepFile(URI.create("fstep:///foo"), UUID.randomUUID());
        fstepFile.setId(1L);
        fstepFile.setDataSource(dataSource);
        fstepFile.setFilesize(585L);

        int defaultCost = costingService.estimateDownloadCost(fstepFile);
        assertThat(defaultCost, is(1));

        CostingExpression costingExpression = CostingExpression.builder()
                .type(CostingExpression.Type.DOWNLOAD)
                .associatedId(1L)
                .costExpression("1")
                .estimatedCostExpression("T(Math).round(2 * T(Math).ceil(filesize/100.0))")
                .build();
        costingExpressionDataService.save(costingExpression);

        int cost = costingService.estimateDownloadCost(fstepFile);
        assertThat(cost, is(2 * (int) Math.ceil((double) fstepFile.getFilesize() / 100)));
        assertThat(cost, is(12));
    }

    @Test
    public void chargeForJob() throws Exception {
        User owner = new User("owner-uid");
        userDataService.save(owner);

        int startingBalance = owner.getWallet().getBalance();

        FstepService service = new FstepService("fstepService", null, "dockerTag");
        service.setId(1L);
        JobConfig jobConfig = new JobConfig();
        jobConfig.setId(1L);
        jobConfig.setService(service);
        Job job = new Job(jobConfig, "jobId", owner);

        costingService.chargeForJob(owner.getWallet(), job);
        assertThat(owner.getWallet().getBalance(), is(startingBalance - 1));

        CostingExpression costingExpression = CostingExpression.builder()
                .type(CostingExpression.Type.SERVICE)
                .associatedId(1L)
                .costExpression("config.service.name.length()")
                .estimatedCostExpression("1")
                .build();
        costingExpressionDataService.save(costingExpression);

        costingService.chargeForJob(owner.getWallet(), job);
        assertThat(owner.getWallet().getBalance(), is(startingBalance - 1 - service.getName().length()));

        List<WalletTransaction> transactions = owner.getWallet().getTransactions();
        assertThat(transactions.size(), is(3));
        assertThat(transactions.get(1).getBalanceChange(), is(-1));
        assertThat(transactions.get(2).getBalanceChange(), is(-service.getName().length()));
    }

    @Test
    public void chargeForDownload() throws Exception {
        User owner = new User("owner-uid");
        userDataService.save(owner);

        int startingBalance = owner.getWallet().getBalance();

        DataSource dataSource = new DataSource();
        dataSource.setId(1L);
        dataSource.setName("FS-TEP DataSource");
        FstepFile fstepFile = new FstepFile(URI.create("fstep:///foo"), UUID.randomUUID());
        fstepFile.setId(1L);
        fstepFile.setDataSource(dataSource);
        fstepFile.setFilesize(329L);

        costingService.chargeForDownload(owner.getWallet(), fstepFile);
        assertThat(owner.getWallet().getBalance(), is(startingBalance - 1));

        CostingExpression costingExpression = CostingExpression.builder()
                .type(CostingExpression.Type.DOWNLOAD)
                .associatedId(1L)
                .costExpression("T(Math).round(2 * T(Math).ceil(filesize/100.0))")
                .build();
        costingExpressionDataService.save(costingExpression);

        costingService.chargeForDownload(owner.getWallet(), fstepFile);
        assertThat(owner.getWallet().getBalance(), is(startingBalance - 1 - (2 * (int) Math.ceil(fstepFile.getFilesize() / 100.0))));

        List<WalletTransaction> transactions = owner.getWallet().getTransactions();
        assertThat(transactions.size(), is(3));
        assertThat(transactions.get(1).getBalanceChange(), is(-1));
        assertThat(transactions.get(2).getBalanceChange(), is(-(2 * (int) Math.ceil(fstepFile.getFilesize() / 100.0))));
    }

}