package ru.pulsar.jenkins.library.steps

import ru.pulsar.jenkins.library.IStepExecutor
import ru.pulsar.jenkins.library.configuration.BranchAnalysisConfiguration
import ru.pulsar.jenkins.library.configuration.JobConfiguration
import ru.pulsar.jenkins.library.configuration.ResultsTransformerType
import ru.pulsar.jenkins.library.configuration.SourceFormat
import ru.pulsar.jenkins.library.ioc.ContextRegistry
import ru.pulsar.jenkins.library.utils.Logger
import ru.pulsar.jenkins.library.utils.StringJoiner
import ru.pulsar.jenkins.library.utils.VersionParser

class SonarScanner implements Serializable {

    private final JobConfiguration config

    SonarScanner(JobConfiguration config) {
        this.config = config
    }

    def run() {
        IStepExecutor steps = ContextRegistry.getContext().getStepExecutor()

        Logger.printLocation()

        if (!config.stageFlags.sonarqube) {
            steps.echo("SonarQube step is disabled")
            return
        }

        def env = steps.env()

        def sonarScannerBinary

        if (config.sonarQubeOptions.useSonarScannerFromPath) {
            sonarScannerBinary = "sonar-scanner"
        } else {
            String scannerHome = steps.tool(config.sonarQubeOptions.sonarScannerToolName)
            sonarScannerBinary = "$scannerHome/bin/sonar-scanner"
        }

        String sonarCommand = "$sonarScannerBinary"

        def branchAnalysisConfiguration = config.sonarQubeOptions.branchAnalysisConfiguration
        if (branchAnalysisConfiguration == BranchAnalysisConfiguration.FROM_ENV) {
            if (env.CHANGE_ID){
                sonarCommand += " -Dsonar.pullrequest.key=$env.CHANGE_ID"
                sonarCommand += " -Dsonar.pullrequest.branch=$env.CHANGE_BRANCH"
                sonarCommand += " -Dsonar.pullrequest.base=$env.CHANGE_TARGET"
            } else {
                sonarCommand += " -Dsonar.branch.name=$env.BRANCH_NAME"
            }
        } else (branchAnalysisConfiguration == BranchAnalysisConfiguration.AUTO) {
            // no-op
        }

        String projectVersion = computeProjectVersion()
        if (projectVersion) {
            sonarCommand += " -Dsonar.projectVersion=$projectVersion"
        }

        if (config.stageFlags.edtValidate) {
            steps.unstash(ResultsTransformer.RESULT_STASH)

            if (config.resultsTransformOptions.transformer == ResultsTransformerType.STEBI) {
                sonarCommand += " -Dsonar.externalIssuesReportPaths=" + ResultsTransformer.RESULT_FILE
            } else {
                sonarCommand += " -Dsonar.bsl.languageserver.reportPaths=" + ResultsTransformer.RESULT_FILE
            }
        }

        def stageFlags = config.stageFlags

        StringJoiner coveragePathsConstructor = new StringJoiner(",")

        if (stageFlags.bdd && config.bddOptions.coverage) {
            steps.unstash(Bdd.COVERAGE_STASH_NAME)
            coveragePathsConstructor.add(Bdd.COVERAGE_STASH_PATH)
        }

        if (stageFlags.smoke && config.smokeTestOptions.coverage) {
            steps.unstash(SmokeTest.COVERAGE_STASH_NAME)
            coveragePathsConstructor.add(SmokeTest.COVERAGE_STASH_PATH)
        }

        if (stageFlags.yaxunit && config.yaxunitOptions.coverage) {
            steps.unstash(Yaxunit.COVERAGE_STASH_NAME)
            coveragePathsConstructor.add(Yaxunit.COVERAGE_STASH_PATH)
        }

        String coveragePaths = coveragePathsConstructor.toString()

        if (!coveragePaths.isEmpty()) {
            sonarCommand += " -Dsonar.coverageReportPaths=${coveragePaths}"
        }

        if (config.sonarQubeOptions.waitForQualityGate) {
            def timeoutInSeconds = config.timeoutOptions.sonarqube * 60
            sonarCommand += ' -Dsonar.qualitygate.wait=true'
            sonarCommand += " -Dsonar.qualitygate.timeout=${timeoutInSeconds}"
        }

        def sonarQubeInstallation = config.sonarQubeOptions.sonarQubeInstallation
        if (sonarQubeInstallation == '') {
            sonarQubeInstallation = null
        }

        steps.withSonarQubeEnv(sonarQubeInstallation) {
            steps.cmd(sonarCommand)
        }
    }

    private String computeProjectVersion() {
        String projectVersion
        String nameOfModule = config.sonarQubeOptions.infoBaseUpdateModuleName

        if (!nameOfModule.isEmpty()) {
            String rootFile
            if (config.sourceFormat == SourceFormat.EDT) {
                rootFile = "$config.srcDir/src/CommonModules/$nameOfModule/Module.bsl"
            } else {
                rootFile = "$config.srcDir/CommonModules/$nameOfModule/Ext/Module.bsl"
            }
            projectVersion = VersionParser.ssl(rootFile)
        } else if (config.sourceFormat == SourceFormat.EDT) {
            String rootFile = "$config.srcDir/src/Configuration/Configuration.mdo"
            projectVersion = VersionParser.edt(rootFile)
        } else {
            String rootFile = "$config.srcDir/Configuration.xml"
            projectVersion = VersionParser.configuration(rootFile)
        }

        return projectVersion
    }
}
