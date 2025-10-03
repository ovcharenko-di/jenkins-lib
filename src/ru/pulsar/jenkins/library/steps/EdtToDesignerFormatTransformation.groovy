package ru.pulsar.jenkins.library.steps

import ru.pulsar.jenkins.library.edt.EdtCliEngineFactory
import ru.pulsar.jenkins.library.IStepExecutor
import ru.pulsar.jenkins.library.configuration.JobConfiguration
import ru.pulsar.jenkins.library.configuration.SourceFormat
import ru.pulsar.jenkins.library.ioc.ContextRegistry
import ru.pulsar.jenkins.library.utils.FileUtils
import ru.pulsar.jenkins.library.utils.Logger

class EdtToDesignerFormatTransformation implements Serializable {

    public static final String WORKSPACE = 'build/edt-workspace'
    public static final String CONFIGURATION_DIR = 'build/cf'
    public static final String CONFIGURATION_ZIP = 'build/cf.zip'
    public static final String CONFIGURATION_ZIP_STASH = 'cf-zip'
    public static final String EXTENSION_DIR = 'build/cfe_src'
    public static final String EXTENSION_ZIP = 'build/cfe_src.zip'
    public static final String EXTENSION_ZIP_STASH = 'cfe_src-zip'

    private final JobConfiguration config;

    EdtToDesignerFormatTransformation(JobConfiguration config) {
        this.config = config
    }

    def run() {
        IStepExecutor steps = ContextRegistry.getContext().getStepExecutor()

        Logger.printLocation()

        if (config.sourceFormat != SourceFormat.EDT) {
            Logger.println("SRC is not in EDT format. No transform is needed.")
            return
        }

        def env = steps.env();

        String workspaceDir = FileUtils.getFilePath("$env.WORKSPACE/$WORKSPACE").getRemote()
        steps.deleteDir(workspaceDir)

        def engine = EdtCliEngineFactory.getEngine(config.edtVersion)

        // конфигурация, сконвертированная в формат конфигуратора, могла быть восстановлена из кэша
        if (!steps.fileExists(CONFIGURATION_ZIP)) {
            engine.edtToDesignerTransformConfiguration(steps, config)
            steps.zip(CONFIGURATION_DIR, CONFIGURATION_ZIP)
        }
        steps.stash(CONFIGURATION_ZIP_STASH, CONFIGURATION_ZIP)

        if (config.needLoadExtensions()) {
            // расширения, сконвертированные в формат конфигуратора, могли быть восстановлены из кэша
            if (!steps.fileExists(EXTENSION_ZIP)) {
                engine.edtToDesignerTransformExtensions(steps, config)
                steps.zip(EXTENSION_DIR, EXTENSION_ZIP)
            }
            steps.stash(EXTENSION_ZIP_STASH, EXTENSION_ZIP)
        }
    }
}
