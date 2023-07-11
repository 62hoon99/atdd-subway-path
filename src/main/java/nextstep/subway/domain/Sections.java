package nextstep.subway.domain;

import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Embeddable
public class Sections {

    private static final String DUPLICATE_STATION_ERROR_MESSAGE = "상행역과 하행역이 이미 노선에 모두 등록되어 있는 경우 등록 불가능 합니다.";
    private static final String NOT_EXISTED_STATION_ERROR_MESSAGE = "상행역과 하행역 둘 중 하나도 노선에 포함되어있지 않은 경우 등록 불가능 합니다.";

    @OneToMany(mappedBy = "line", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private final List<Section> sections = new ArrayList<>();

    public int size() {
        return sections.size();
    }

    public void add(Section newSection) {
        validateNewSection(newSection);

        if (sections.isEmpty() || shouldAddNewUpEndSection(newSection) || shouldAddNewDownEndSection(newSection)) {
            sections.add(newSection);
            return;
        }
        if (shouldAddSectionBetweenStation(newSection)) {
            addSectionBetweenStation(newSection);
            return;
        }

        throw new IllegalArgumentException();
    }

    public List<Station> getStations() {
        Station upEndStation = getUpEndStation();

        List<Station> findStations = new ArrayList<>();
        findStations.add(upEndStation);

        Stack<Station> upStationStack = new Stack<>();
        upStationStack.push(upEndStation);

        while (!upStationStack.isEmpty()) {
            Station currentStation = upStationStack.pop();
            sections.stream()
                    .filter(section -> section.isUpStation(currentStation))
                    .findAny()
                    .ifPresent(section -> {
                        Station nextStation = section.getDownStation();
                        findStations.add(nextStation);
                        upStationStack.push(nextStation);
                    });
        }

        return findStations;
    }

    public void remove(Section... section) {
        sections.removeAll(List.of(section));
    }

    public void removeLastSection(Station station) {
        if (sections.isEmpty() || !sections.get(sections.size() - 1).isDownStation(station)) {
            throw new IllegalArgumentException();
        }
        sections.remove(sections.get(sections.size() - 1));
    }

    private Station getUpEndStation() {
        return sections.stream()
                .map(Section::getUpStation)
                .filter(upStation -> sections.stream().noneMatch(section -> section.isDownStation(upStation)))
                .findAny()
                .orElseThrow(IllegalStateException::new);
    }

    private Station getDownEndStation() {
        return sections.stream()
                .map(Section::getDownStation)
                .filter(downStation -> sections.stream().noneMatch(section -> section.isUpStation(downStation)))
                .findAny()
                .orElseThrow(IllegalStateException::new);
    }

    private void addSectionBetweenStation(Section newSection) {
        Section findSection = sections.stream().filter(s -> s.isUpStation(newSection.getUpStation()))
                .findAny().orElseThrow(IllegalArgumentException::new);
        findSection.changeUpStation(newSection.getDownStation());
        findSection.subtractDistance(newSection.getDistance());
        sections.add(newSection);
    }

    private boolean shouldAddSectionBetweenStation(Section newSection) {
        return sections.stream().anyMatch(section -> section.isUpStation(newSection.getUpStation()));
    }

    private boolean shouldAddNewUpEndSection(Section newSection) {
        return getUpEndStation().equals(newSection.getDownStation());
    }

    private boolean shouldAddNewDownEndSection(Section newSection) {
        return getDownEndStation().equals(newSection.getUpStation());
    }

    private void validateNewSection(Section newSection) {
        Set<Station> stationsSet = getStationsSet();
        if (stationsSet.contains(newSection.getDownStation()) && stationsSet.contains(newSection.getUpStation())) {
            throw new IllegalArgumentException(DUPLICATE_STATION_ERROR_MESSAGE);
        }
        if (!sections.isEmpty() && !stationsSet.contains(newSection.getDownStation())
                && !stationsSet.contains(newSection.getUpStation())) {
            throw new IllegalArgumentException(NOT_EXISTED_STATION_ERROR_MESSAGE);
        }
    }

    private Set<Station> getStationsSet() {
        return sections.stream()
                .flatMap(section -> Stream.of(section.getUpStation(), section.getDownStation()))
                .collect(Collectors.toSet());
    }

}
