package src;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;

public class CodeAggregator {

    public static void main(String[] args) {
        // 1. Проверяем количество аргументов
        if (args.length != 3) {
            printUsage();
            System.exit(1); // Выход с кодом ошибки
        }

        Path sourceDir;
        Path destDir;
        String outputFileName = args[2];
        Path outputFile;

        try {
            // 2. Получаем пути из аргументов
            sourceDir = Paths.get(args[0]);
            destDir = Paths.get(args[1]);

            // 3. Валидация путей
            if (!Files.isDirectory(sourceDir)) {
                System.err.println("Ошибка: Исходный путь не является директорией или не существует: " + sourceDir);
                System.exit(1);
            }

            // Создаем директорию назначения, если ее нет
            if (!Files.exists(destDir)) {
                try {
                    Files.createDirectories(destDir);
                    System.out.println("Создана директория назначения: " + destDir.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Ошибка: Не удалось создать директорию назначения: " + destDir);
                    e.printStackTrace();
                    System.exit(1);
                }
            } else if (!Files.isDirectory(destDir)) {
                System.err.println("Ошибка: Путь назначения существует, но не является директорией: " + destDir);
                System.exit(1);
            }

            // Формируем полный путь к выходному файлу
            outputFile = destDir.resolve(outputFileName);

            System.out.println("Исходная директория: " + sourceDir.toAbsolutePath());
            System.out.println("Выходной файл:       " + outputFile.toAbsolutePath());
            System.out.println("Начинаю обработку файлов...");

            // 4. Вызываем основной метод агрегации
            aggregateFiles(sourceDir, outputFile);

            System.out.println("\nАгрегация завершена. Результат записан в: " + outputFile.toAbsolutePath());

        } catch (InvalidPathException e) {
            System.err.println("Ошибка: Указан неверный формат пути.");
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Произошла ошибка ввода-вывода:");
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Произошла непредвиденная ошибка:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void aggregateFiles(Path sourceDir, Path outputFile) throws IOException {
        // StandardOpenOption.CREATE - создать файл, если его нет
        // StandardOpenOption.TRUNCATE_EXISTING - перезаписать файл, если он существует
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))
        {
            // Рекурсивно обходим все файлы и папки в sourceDir
            // Используем Files.walk для получения Stream<Path>
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths
                        .filter(Files::isRegularFile) // Оставляем только обычные файлы (не директории)
                        // --- Опционально: фильтрация по расширению ---
                        // .filter(p -> {
                        //     String fileName = p.toString().toLowerCase();
                        //     return fileName.endsWith(".java") || fileName.endsWith(".xml") || fileName.endsWith(".txt");
                        // })
                        // --- Конец опциональной фильтрации ---
                        .sorted() // Сортируем файлы по пути для предсказуемого порядка
                        .forEach(filePath -> {
                            // Получаем относительный путь для вывода
                            Path relativePath = sourceDir.relativize(filePath);
                            System.out.println("  -> Добавляю файл: " + relativePath);
                            try {
                                // Записываем разделитель с именем файла
                                writer.write("/* === Начало файла: " + relativePath + " === */");
                                writer.newLine();
                                writer.newLine(); // Пустая строка для разделения

                                // Читаем содержимое файла и пишем в выходной файл
                                // Используем Files.lines для потоковой обработки, эффективно для больших файлов
                                try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                                    lines.forEach(line -> {
                                        try {
                                            writer.write(line);
                                            writer.newLine();
                                        } catch (IOException e) {
                                            throw new RuntimeException("Ошибка записи строки из файла: " + filePath, e);
                                        }
                                    });
                                } catch (IOException e) {
                                    System.err.println("Предупреждение: Не удалось прочитать файл: " + filePath + " (" + e.getMessage() + ")");
                                    writer.newLine();
                                    writer.write("/* !!! Ошибка чтения файла: " + filePath + " (" + e.getMessage() + ") !!! */");
                                    writer.newLine();
                                } catch (Exception e) {
                                    System.err.println("Предупреждение: Неожиданная ошибка при обработке файла: " + filePath + " (" + e.getMessage() + ")");
                                    writer.newLine();
                                    writer.write("/* !!! Неожиданная ошибка при обработке файла: " + filePath + " (" + e.getMessage() + ") !!! */");
                                    writer.newLine();
                                }

                                // Записываем разделитель конца файла
                                writer.newLine();
                                writer.write("/* === Конец файла: " + relativePath + " === */");
                                writer.newLine();
                                writer.newLine();

                            } catch (IOException e) {
                                System.err.println("Критическая ошибка при записи данных для файла: " + relativePath);
                                throw new RuntimeException(e);
                            } catch (RuntimeException e) {
                                System.err.println("Критическая ошибка при обработке файла: " + relativePath);
                                throw e; // Перебрасываем дальше, чтобы остановить процесс
                            }
                        });
            }
        }
        catch (RuntimeException e) {
            // Перехватываем RuntimeException, если он "вылетел" из forEach
            // и извлекаем исходную причину, если это была IOException
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw e; // Перебрасываем другие RuntimeException
            }
        }
    }

    private static void printUsage() {
        System.out.println("Использование: java -jar code-aggregator.jar <source_directory> <destination_directory> <output_filename>");
        System.out.println();
        System.out.println("Аргументы:");
        System.out.println("  <source_directory>      - Путь к папке с исходными файлами (будет обработана рекурсивно).");
        System.out.println("  <destination_directory> - Путь к папке, куда будет сохранен результат.");
        System.out.println("                          (Будет создана, если не существует).");
        System.out.println("  <output_filename>       - Имя для итогового текстового файла.");
        System.out.println();
        System.out.println("Пример:");
        System.out.println("  java -jar code-aggregator.jar ./my_project/src ./output combined_code.txt");
    }
}